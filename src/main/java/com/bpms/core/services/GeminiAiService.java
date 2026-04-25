package com.bpms.core.services;

import com.bpms.core.dto.ia.FlujoGeneradoIA;
import com.bpms.core.dto.ia.FlujoGeneradoResponse;
import com.bpms.core.models.Departamento;
import com.bpms.core.repositories.DepartamentoRepository;

import com.bpms.core.dto.ia.ChatbotResponse;
import com.bpms.core.models.EstadoProceso;
import com.bpms.core.models.Paso;
import com.bpms.core.models.ProcesoDefinicion;
import com.bpms.core.models.Tramite;
import com.bpms.core.repositories.ProcesoDefinicionRepository;
import com.bpms.core.repositories.TramiteRepository;

import org.springframework.beans.factory.annotation.Autowired;
import java.text.Normalizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiAiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    // 👇 NUEVO: Definimos la ruta base donde ArchivoController guarda los archivos.
    private static final String CARPETA_BASE = "uploads";

    private final RestTemplate restTemplate;

    // 👇 NUEVO CU17
    @Autowired
    private DepartamentoRepository departamentoRepository;

    // 👇 NUEVO Asistente IA Cliente
    @Autowired
    private ProcesoDefinicionRepository procesoRepository;

    // 👇 NUEVO Asistente IA Cliente
    @Autowired
    private TramiteRepository tramiteRepository;
    // 👇 NUEVO CU17

    public GeminiAiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // Aumentamos el timeout a 15s porque subir PDFs en Base64 toma un poco más.
        factory.setConnectTimeout(60000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    // 👇 NUEVO: Ahora recibimos la lista de rutas relativas de los archivos.
    // 👇 NUEVO: Eliminamos 'nombreCampo' de los parámetros
    // 👇 NUEVO: sin ofuscación, prompt explícito por tipo de campo
    public String generarSugerencia(String contextoFormulario, String descripcionTramite, List<String> urlsArchivos) {

        // 👇 NUEVO: Prompt estricto que le enseña a Gemini cómo formatear cada tipo
        String promptText = String.format(
                "Eres un analista experto de un sistema BPMS. Analiza el relato del trámite " +
                        "y los documentos adjuntos, luego rellena el formulario que te paso.\n\n" +
                        "RELATO DEL TRÁMITE:\n%s\n\n" +
                        "FORMULARIO A RELLENAR (array de campos con su definición):\n%s\n\n" +
                        "REGLAS ESTRICTAS:\n" +
                        "1. Devuelve SOLO un objeto JSON válido, sin markdown ni texto extra.\n" +
                        "2. Las CLAVES del JSON deben ser EXACTAMENTE el valor del campo `id` de cada objeto del formulario. NUNCA uses la `etiqueta` como clave.\n"
                        +
                        "3. Los VALORES deben respetar estrictamente el `tipo` de cada campo:\n" +
                        "   - texto, textarea, email, telefono: string breve y relevante.\n" +
                        "   - numero: número (no string, sin comillas).\n" +
                        "   - fecha: string formato 'YYYY-MM-DD'.\n" +
                        "   - hora: string formato 'HH:mm'.\n" +
                        "   - fecha_hora: string formato 'YYYY-MM-DDTHH:mm'.\n" +
                        "   - si_no: exactamente la string 'SI' o 'NO' (en mayúsculas, sin tildes).\n" +
                        "   - calificacion: número entero entre 1 y `escalaMax` (por defecto 1..5). NUNCA devuelvas texto ni explicación aquí, SOLO el número.\n"
                        +
                        "   - seleccion, radio: uno de los `valor` presentes en el array `opcionesList` del campo.\n" +
                        "   - checkbox: array de strings, cada uno siendo un `valor` del `opcionesList`.\n" +
                        "   - tabla: array de objetos. Cada objeto usa los `id` de `columnasTabla` como claves.\n" +
                        "4. Si un campo es del tipo `archivo` o `imagen`, OMÍTELO del JSON (no puedes generar archivos).\n"
                        +
                        "5. Si no puedes inferir un valor razonable para un campo, OMÍTELO (no inventes datos sin sustento).\n"
                        +
                        "6. Basa tus respuestas en el relato Y en el contenido de los documentos adjuntos (PDFs/imágenes).",
                descripcionTramite == null ? "" : descripcionTramite,
                contextoFormulario == null ? "" : contextoFormulario);

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", promptText));

        if (urlsArchivos != null) {
            for (String urlRelativa : urlsArchivos) {
                Map<String, Object> filePart = procesarArchivoParaGemini(urlRelativa);
                if (filePart != null) {
                    parts.add(filePart);
                }
            }
        }

        // 👇 NUEVO: construir request body + headers + entity ANTES del loop
        Map<String, Object> generationConfig = Map.of(
                "responseMimeType", "application/json");

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", generationConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 👇 NUEVO: lista de modelos con fallback automático si uno está saturado
        String[] modelos = {
                "gemini-2.5-flash",
                "gemini-2.5-flash-lite",
                "gemini-2.0-flash"
        };

        Exception ultimoError = null;
        for (String modelo : modelos) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + modelo + ":generateContent?key=" + apiKey;
            try {
                Map response = restTemplate.postForObject(url, entity, Map.class);
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> responseParts = (List<Map<String, Object>>) content.get("parts");
                return (String) responseParts.get(0).get("text");
            } catch (Exception e) {
                ultimoError = e;
                // Si es 503/429/UNAVAILABLE, reintentar con siguiente modelo
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("503") || msg.contains("429") || msg.contains("UNAVAILABLE")
                        || msg.contains("overloaded")) {
                    System.err.println("⚠️ Modelo " + modelo + " saturado, probando siguiente...");
                    continue;
                }
                // Otro tipo de error: no tiene sentido reintentar con otro modelo
                throw new RuntimeException("Error en API de IA: " + e.getMessage());
            }
        }
        throw new RuntimeException("Todos los modelos Gemini están saturados. Intenta en unos minutos. Último error: "
                + (ultimoError != null ? ultimoError.getMessage() : "desconocido"));
    }

    // 👇 NUEVO: Función para leer el archivo del disco y pasarlo al formato Gemini
    private Map<String, Object> procesarArchivoParaGemini(String urlRelativa) {
        try {
            // Ejemplo urlRelativa: "/api/archivos/ver/69de3.../uuid.pdf"
            // Extraemos solo "69de3.../uuid.pdf" o "uuid.pdf"
            String rutaLogica = urlRelativa.replace("/api/archivos/ver/", "");
            Path filePath = Paths.get(CARPETA_BASE, rutaLogica);

            if (Files.exists(filePath)) {
                String mimeType = Files.probeContentType(filePath);
                // Gemini soporta PDFs, PNGs, JPGs, WebP, etc.
                if (mimeType == null)
                    mimeType = "application/octet-stream";

                byte[] fileBytes = Files.readAllBytes(filePath);
                String base64Data = Base64.getEncoder().encodeToString(fileBytes);

                Map<String, String> inlineData = new HashMap<>();
                inlineData.put("mimeType", mimeType);
                inlineData.put("data", base64Data);

                return Map.of("inlineData", inlineData);
            }
        } catch (IOException e) {
            System.err.println("No se pudo leer el archivo para la IA: " + e.getMessage());
        }
        return null; // Si falla, simplemente no lo incluimos
    }

    private String ofuscarDatosSensibles(String texto) {
        if (texto == null)
            return "";
        texto = texto.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[CORREO_OCULTO]");
        texto = texto.replaceAll("\\b\\d{7,15}\\b", "[NUMERO_OCULTO]");
        return texto;
    }

    // 👇 NUEVO CU17: método robusto que valida, matchea y enriquece la respuesta de la IA
    public FlujoGeneradoResponse generarFlujoBpmn(String promptAdmin, String departamentosDisponibles) {

        // 1. Llamar a la IA y obtener el JSON crudo
        String jsonCrudo = invocarGeminiParaFlujo(promptAdmin, departamentosDisponibles);

        // 2. Parsear el JSON a estructura tipada
        // 2. Parsear el JSON a estructura tipada
        // 2. Parsear el JSON a estructura tipada (usando JsonParserFactory de Spring Boot)
        FlujoGeneradoIA flujo;
        try {
            flujo = parsearJsonAFlujo(jsonCrudo);
        } catch (Exception e) {
            throw new RuntimeException("FLUJO_INCOHERENTE: La IA devolvió un JSON inválido. " + e.getMessage());
        }

        // 3. Validar estructura mínima
        if (flujo.getDepartamentos() == null || flujo.getDepartamentos().isEmpty()) {
            throw new RuntimeException("FLUJO_INCOHERENTE: La IA no identificó ningún departamento.");
        }
        if (flujo.getNodos() == null || flujo.getNodos().size() < 2) {
            throw new RuntimeException("FLUJO_INCOHERENTE: La IA no generó suficientes nodos para construir un flujo.");
        }
        if (flujo.getConexiones() == null || flujo.getConexiones().isEmpty()) {
            throw new RuntimeException("FLUJO_INCOHERENTE: La IA no generó conexiones entre los nodos.");
        }

        // 4. Validar que tenga StartEvent y EndEvent
        boolean tieneStart = flujo.getNodos().stream().anyMatch(n -> "StartEvent".equalsIgnoreCase(n.getTipo()));
        boolean tieneEnd = flujo.getNodos().stream().anyMatch(n -> "EndEvent".equalsIgnoreCase(n.getTipo()));
        if (!tieneStart || !tieneEnd) {
            throw new RuntimeException("FLUJO_INCOHERENTE: El flujo debe tener al menos un inicio y un fin.");
        }

        // 5. Matchear departamentos contra BD y construir respuesta enriquecida
        FlujoGeneradoResponse response = new FlujoGeneradoResponse();
        List<String> advertencias = new ArrayList<>();
        List<String> noMatcheados = new ArrayList<>();

        // Cargar departamentos reales de BD (incluyendo el "Cliente" virtual)
        List<Departamento> deptosBD = departamentoRepository.findAll();

        // Matchear cada departamento que devolvió la IA
        List<String> deptosNormalizados = new ArrayList<>();
        for (String deptoIA : flujo.getDepartamentos()) {
            String matcheado = matchearDepartamento(deptoIA, deptosBD);
            if (matcheado == null) {
                // No hubo match → registrar en no-matcheados
                noMatcheados.add(deptoIA);
                advertencias.add("⚠️ El departamento \"" + deptoIA + "\" no existe en BD. Crea uno nuevo o reasigna manualmente.");
                deptosNormalizados.add(deptoIA); // se mantiene el nombre original
            } else if (!matcheado.equalsIgnoreCase(deptoIA)) {
                // Match parcial → avisar
                advertencias.add("ℹ️ Asigné \"" + deptoIA + "\" → \"" + matcheado + "\" por similitud.");
                deptosNormalizados.add(matcheado);
            } else {
                // Match exacto, no hace falta avisar
                deptosNormalizados.add(matcheado);
            }
        }
        flujo.setDepartamentos(deptosNormalizados);

        // 6. Reemplazar también las referencias en cada nodo (deptos viejos → matcheados)
        for (FlujoGeneradoIA.NodoIA nodo : flujo.getNodos()) {
            String deptoIA = nodo.getDepartamento();
            if (deptoIA == null) continue;
            String matcheado = matchearDepartamento(deptoIA, deptosBD);
            if (matcheado != null) {
                nodo.setDepartamento(matcheado);
            }
        }

        response.setFlujo(flujo);
        response.setAdvertencias(advertencias);
        response.setDepartamentosNoMatcheados(noMatcheados);
        response.setTotalNodos(flujo.getNodos().size());
        response.setTotalConexiones(flujo.getConexiones().size());

        return response;
    }

    /**
     * 👇 NUEVO CU17: matchea el nombre de la IA contra los departamentos reales en BD.
     *
     * Estrategia:
     * 1. Match exacto case-insensitive
     * 2. Match exacto sin tildes ni espacios extras
     * 3. Match por inclusión (BD contiene IA o viceversa)
     * 4. Match especial: "Cliente / Solicitante" → "Cliente / Solicitante" (virtual)
     *
     * Retorna el nombre canónico del departamento, o null si no encuentra match.
     */
    private String matchearDepartamento(String nombreIA, List<Departamento> deptosBD) {
        if (nombreIA == null || nombreIA.isBlank()) return null;

        String iaNorm = normalizar(nombreIA);

        // Caso especial: cliente virtual
        if (iaNorm.contains("cliente") || iaNorm.contains("solicitante")) {
            return "Cliente / Solicitante";
        }

        // 1. Match exacto case-insensitive
        for (Departamento d : deptosBD) {
            if (d.getNombre().equalsIgnoreCase(nombreIA)) {
                return d.getNombre();
            }
        }

        // 2. Match exacto normalizado
        for (Departamento d : deptosBD) {
            if (normalizar(d.getNombre()).equals(iaNorm)) {
                return d.getNombre();
            }
        }

        // 3. Match por inclusión
        for (Departamento d : deptosBD) {
            String bdNorm = normalizar(d.getNombre());
            if (bdNorm.contains(iaNorm) || iaNorm.contains(bdNorm)) {
                return d.getNombre();
            }
        }

        return null;
    }

    /**
     * Normaliza para comparar: minúsculas, sin tildes, sin espacios extras.
     */
    private String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    /**
     * 👇 NUEVO CU17: la llamada cruda a Gemini, separada para reutilizar el fallback.
     */
    private String invocarGeminiParaFlujo(String promptAdmin, String departamentosDisponibles) {
        String promptText = "Eres un experto arquitecto de procesos de negocio (BPMN). " +
                "Basado en la siguiente descripción del administrador, genera una estructura JSON estricta " +
                "que represente el flujo de trabajo.\n\n" +
                "DESCRIPCIÓN DEL FLUJO:\n" + promptAdmin + "\n\n" +
                "REGLAS ESTRICTAS:\n" +
                "1. Devuelve SOLO un objeto JSON válido, sin markdown ni comillas invertidas.\n" +
                "2. El JSON debe tener exactamente 3 claves: 'departamentos', 'nodos' y 'conexiones'.\n" +
                "3. 'departamentos': Array de strings. SOLO puedes usar nombres de esta lista exacta: [" + departamentosDisponibles + "]. NUNCA inventes nombres nuevos. Si no hay match perfecto, usa el más parecido de la lista.\n" +
                "4. 'nodos': Array de objetos. Cada objeto debe tener:\n" +
                "   - 'id': identificador único alfanumérico (ej: StartEvent_1, Task_1, Gateway_1, EndEvent_1)\n" +
                "   - 'tipo': SOLO uno de: StartEvent, UserTask, ExclusiveGateway, ParallelGateway, EndEvent\n" +
                "   - 'nombre': etiqueta descriptiva (ej: 'Revisar documentos', '¿Está aprobado?')\n" +
                "   - 'departamento': nombre EXACTO de uno del array 'departamentos' anterior.\n" +
                "5. 'conexiones': Array de objetos con:\n" +
                "   - 'origen': id del nodo de salida\n" +
                "   - 'destino': id del nodo de llegada\n" +
                "   - 'nombre': texto de la flecha. OBLIGATORIO si origen es ExclusiveGateway (usar 'APROBADO'/'RECHAZADO' o 'SI'/'NO'). Para los demás, déjalo vacío ''.\n\n" +
                "REGLAS LÓGICAS:\n" +
                "- DEBE haber EXACTAMENTE UN StartEvent y AL MENOS UN EndEvent.\n" +
                "- Todo nodo (excepto EndEvent) debe tener al menos una conexión saliente.\n" +
                "- Todo ExclusiveGateway debe tener AL MENOS 2 conexiones salientes con nombres distintos.\n" +
                "- Si la descripción es ambigua o no se puede inferir un flujo lógico, devuelve un JSON con array 'nodos' vacío.";

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", promptText));

        Map<String, Object> generationConfig = Map.of("responseMimeType", "application/json");
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", generationConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String[] modelos = { "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash" };

        Exception ultimoError = null;
        for (String modelo : modelos) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + modelo + ":generateContent?key=" + apiKey;
            try {
                Map response = restTemplate.postForObject(url, entity, Map.class);
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> responseParts = (List<Map<String, Object>>) content.get("parts");
                String jsonResult = (String) responseParts.get(0).get("text");
                return jsonResult.replace("```json", "").replace("```", "").trim();
            } catch (Exception e) {
                ultimoError = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("503") || msg.contains("429") || msg.contains("UNAVAILABLE")
                        || msg.contains("overloaded")) {
                    System.err.println("⚠️ Modelo " + modelo + " saturado en CU17, probando siguiente...");
                    continue;
                }
                throw new RuntimeException("Error en API de IA para Generar Flujo: " + e.getMessage());
            }
        }
        throw new RuntimeException("IA_SATURADA: Todos los modelos Gemini están saturados. Intenta en unos minutos.");
    }
    /**
     * 👇 NUEVO CU17: parseo manual del JSON usando JsonParserFactory de Spring Boot.
     * Evita la dependencia directa con Jackson ObjectMapper.
     */
    @SuppressWarnings("unchecked")
    private FlujoGeneradoIA parsearJsonAFlujo(String jsonCrudo) {
        org.springframework.boot.json.JsonParser jsonParser =
                org.springframework.boot.json.JsonParserFactory.getJsonParser();
        Map<String, Object> raw = jsonParser.parseMap(jsonCrudo);

        FlujoGeneradoIA flujo = new FlujoGeneradoIA();

        // departamentos
        Object deptsObj = raw.get("departamentos");
        if (deptsObj instanceof List) {
            List<String> deptos = new ArrayList<>();
            for (Object o : (List<?>) deptsObj) deptos.add(String.valueOf(o));
            flujo.setDepartamentos(deptos);
        }

        // nodos
        Object nodosObj = raw.get("nodos");
        if (nodosObj instanceof List) {
            List<FlujoGeneradoIA.NodoIA> nodos = new ArrayList<>();
            for (Object o : (List<?>) nodosObj) {
                Map<String, Object> m = (Map<String, Object>) o;
                FlujoGeneradoIA.NodoIA nodo = new FlujoGeneradoIA.NodoIA();
                nodo.setId(strOrNull(m.get("id")));
                nodo.setTipo(strOrNull(m.get("tipo")));
                nodo.setNombre(strOrNull(m.get("nombre")));
                nodo.setDepartamento(strOrNull(m.get("departamento")));
                nodos.add(nodo);
            }
            flujo.setNodos(nodos);
        }

        // conexiones
        Object connObj = raw.get("conexiones");
        if (connObj instanceof List) {
            List<FlujoGeneradoIA.ConexionIA> conns = new ArrayList<>();
            for (Object o : (List<?>) connObj) {
                Map<String, Object> m = (Map<String, Object>) o;
                FlujoGeneradoIA.ConexionIA c = new FlujoGeneradoIA.ConexionIA();
                c.setOrigen(strOrNull(m.get("origen")));
                c.setDestino(strOrNull(m.get("destino")));
                c.setNombre(strOrNull(m.get("nombre")));
                conns.add(c);
            }
            flujo.setConexiones(conns);
        }

        return flujo;
    }

    private String strOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }
    /**
     * 👇 NUEVO Asistente IA Cliente: chatbot conversacional para clientes.
     * Construye contexto con catálogo de trámites ACTIVAS + trámites del cliente,
     * limita el dominio mediante prompt, y responde en JSON estructurado.
     */
    public ChatbotResponse chatbotCliente(String mensaje, List<Map<String, String>> historial, String clienteId) {

        // 1. Cargar catálogo de trámites ACTIVAS
        List<ProcesoDefinicion> activas = procesoRepository.findByEstado(EstadoProceso.ACTIVA);

        // 2. Cargar trámites del cliente (solo si está logueado como cliente)
        List<Tramite> misTramites = (clienteId != null && !clienteId.equals("SISTEMA"))
                ? tramiteRepository.findByClienteIdOrderByFechaCreacionDesc(clienteId)
                : new ArrayList<>();

        // 3. Construir el "system prompt" en español, acotado al dominio
        StringBuilder ctx = new StringBuilder();
        ctx.append("Eres el asistente virtual oficial del sistema BPMS Core de la institución. ");
        ctx.append("Tu misión es ayudar a los CIUDADANOS/CLIENTES a entender qué trámites pueden iniciar, ");
        ctx.append("cómo se procesan y a consultar el estado de sus solicitudes.\n\n");

        ctx.append("=== CATÁLOGO DE TRÁMITES DISPONIBLES ===\n");
        if (activas.isEmpty()) {
            ctx.append("(No hay trámites activos en este momento.)\n");
        } else {
            for (ProcesoDefinicion p : activas) {
                ctx.append("• ").append(p.getNombre());
                if (p.getCodigo() != null) ctx.append(" [código: ").append(p.getCodigo()).append("]");
                ctx.append("\n");
                if (p.getDescripcion() != null && !p.getDescripcion().isBlank()) {
                    ctx.append("   Descripción: ").append(p.getDescripcion()).append("\n");
                }
                if (p.getPasos() != null && !p.getPasos().isEmpty()) {
                    ctx.append("   Pasos del flujo: ");
                    List<String> nombres = new ArrayList<>();
                    for (Paso paso : p.getPasos()) {
                        if (paso.getNombre() != null && !paso.getNombre().isBlank()) {
                            nombres.add(paso.getNombre());
                        }
                    }
                    ctx.append(String.join(" → ", nombres)).append("\n");
                }
            }
        }

        if (!misTramites.isEmpty()) {
            ctx.append("\n=== TRÁMITES DEL USUARIO ACTUAL (").append(clienteId).append(") ===\n");
            for (Tramite t : misTramites) {
                ctx.append("• Código: ").append(t.getCodigoSeguimiento());
                if (t.getDescripcion() != null) ctx.append(" — ").append(t.getDescripcion());
                if (t.getEstadoSemaforo() != null) ctx.append(" — Estado: ").append(t.getEstadoSemaforo());
                if (t.getPasoActualId() != null) ctx.append(" — Paso actual: ").append(t.getPasoActualId());
                ctx.append("\n");
            }
        } else if (clienteId != null && !clienteId.equals("SISTEMA")) {
            ctx.append("\n=== TRÁMITES DEL USUARIO ACTUAL ===\n");
            ctx.append("(El usuario aún no tiene trámites iniciados.)\n");
        }

        ctx.append("\n=== REGLAS ESTRICTAS ===\n");
        ctx.append("1. Responde SOLO sobre los trámites de esta institución, sus pasos, requisitos o el estado del usuario.\n");
        ctx.append("2. Si te preguntan algo fuera de tema (recetas, deportes, clima, etc.), responde amablemente que solo asistes con trámites institucionales.\n");
        ctx.append("3. NUNCA inventes trámites que no estén en el catálogo. Si no existe, dilo.\n");
        ctx.append("4. NUNCA reveles datos de OTROS usuarios. Solo del usuario actual.\n");
        ctx.append("5. Sé breve, amable y claro. Máximo 4 párrafos cortos. Usa emojis con moderación.\n");
        ctx.append("6. Si te preguntan cómo iniciar un trámite, recomienda usar el botón 'Iniciar Nuevo Trámite' en el panel.\n");
        ctx.append("7. Si te preguntan por el estado de un trámite específico, ofrece la opción 'Rastrear mi Trámite' con el código de seguimiento.\n");
        ctx.append("8. Devuelve la respuesta SIEMPRE en JSON válido con esta forma exacta:\n");
        ctx.append("   {\"respuesta\": \"texto al usuario\", \"sugerenciasRapidas\": [\"pregunta sugerida 1\", \"pregunta sugerida 2\", \"pregunta sugerida 3\"]}\n");
        ctx.append("9. Las sugerencias deben ser preguntas cortas (máx 6 palabras) que el usuario podría querer hacer a continuación. Genera 2 a 3 sugerencias relevantes al hilo.\n");

        // 4. Armar la lista de contents para Gemini (formato role/parts)
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", ctx.toString()))
        ));
        contents.add(Map.of(
                "role", "model",
                "parts", List.of(Map.of("text",
                        "Entendido. Estoy listo para ayudar al ciudadano con consultas sobre los trámites disponibles y el estado de sus solicitudes, respetando las reglas."))
        ));

        // 5. Agregar historial reciente (últimos 10 turnos máximo)
        if (historial != null) {
            int desde = Math.max(0, historial.size() - 10);
            for (int i = desde; i < historial.size(); i++) {
                Map<String, String> m = historial.get(i);
                String rolMsg = "user".equalsIgnoreCase(m.get("rol")) ? "user" : "model";
                String contenido = m.get("contenido");
                if (contenido == null || contenido.isBlank()) continue;
                contents.add(Map.of(
                        "role", rolMsg,
                        "parts", List.of(Map.of("text", contenido))
                ));
            }
        }

        // 6. Mensaje actual del usuario
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", mensaje))
        ));

        Map<String, Object> generationConfig = Map.of("responseMimeType", "application/json");
        Map<String, Object> requestBody = Map.of(
                "contents", contents,
                "generationConfig", generationConfig
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 7. Llamar a Gemini con fallback de 3 modelos
        String[] modelos = { "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash" };
        Exception ultimoError = null;

        for (String modelo : modelos) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + modelo + ":generateContent?key=" + apiKey;
            try {
                Map response = restTemplate.postForObject(url, entity, Map.class);
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> responseParts = (List<Map<String, Object>>) content.get("parts");
                String jsonResult = ((String) responseParts.get(0).get("text"))
                        .replace("```json", "").replace("```", "").trim();
                return parsearChatbotResponse(jsonResult);
            } catch (Exception e) {
                ultimoError = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("503") || msg.contains("429") || msg.contains("UNAVAILABLE")
                        || msg.contains("overloaded")) {
                    System.err.println("⚠️ Modelo " + modelo + " saturado en chatbot, probando siguiente...");
                    continue;
                }
                throw new RuntimeException("Error en chatbot IA: " + e.getMessage());
            }
        }
        throw new RuntimeException("IA_SATURADA: Todos los modelos Gemini están saturados. Intenta en unos segundos. Último error: "
                + (ultimoError != null ? ultimoError.getMessage() : "desconocido"));
    }

    /**
     * 👇 NUEVO Asistente IA Cliente: parseo del JSON de respuesta usando JsonParserFactory.
     */
    @SuppressWarnings("unchecked")
    private ChatbotResponse parsearChatbotResponse(String jsonCrudo) {
        org.springframework.boot.json.JsonParser jsonParser =
                org.springframework.boot.json.JsonParserFactory.getJsonParser();
        Map<String, Object> raw = jsonParser.parseMap(jsonCrudo);

        ChatbotResponse resp = new ChatbotResponse();
        resp.setRespuesta(strOrNull(raw.get("respuesta")));

        Object sugObj = raw.get("sugerenciasRapidas");
        List<String> sugs = new ArrayList<>();
        if (sugObj instanceof List) {
            for (Object o : (List<?>) sugObj) {
                if (o != null) sugs.add(String.valueOf(o));
            }
        }
        resp.setSugerenciasRapidas(sugs);

        return resp;
    }
}