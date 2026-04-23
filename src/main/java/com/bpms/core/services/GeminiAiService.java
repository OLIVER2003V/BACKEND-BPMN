package com.bpms.core.services;

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
}