package com.bpms.core.services;

import com.bpms.core.models.*;
import com.bpms.core.repositories.DepartamentoRepository;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class BpmnParserService {

    @Autowired
    private DepartamentoRepository departamentoRepository;

    public ProcesoDefinicion parsearYRellenar(ProcesoDefinicion definicion, String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new IllegalArgumentException("El XML BPMN está vacío");
        }

        // Preservar campos de formulario definidos en el frontend
        Map<String, List<CampoFormulario>> camposOriginales = new HashMap<>();
        if (definicion.getPasos() != null) {
            for (Paso p : definicion.getPasos()) {
                if (p.getId() != null && p.getCampos() != null) {
                    camposOriginales.put(p.getId(), p.getCampos());
                }
            }
        }
        // Agrega después del bloque de camposOriginales
        Map<String, TipoResponsable> responsablesOriginales = new HashMap<>();
        if (definicion.getPasos() != null) {
            for (Paso p : definicion.getPasos()) {
                if (p.getId() != null && p.getTipoResponsable() != null) {
                    responsablesOriginales.put(p.getId(), p.getTipoResponsable());
                }
            }
        }
        // 👇 NUEVO: preservar camposVisibles del frontend
        Map<String, List<String>> camposVisiblesOriginales = new HashMap<>();
        if (definicion.getPasos() != null) {
            for (Paso p : definicion.getPasos()) {
                if (p.getId() != null && p.getCamposVisibles() != null) {
                    camposVisiblesOriginales.put(p.getId(), p.getCamposVisibles());
                }
            }
        }

        BpmnModelInstance modelo = Bpmn.readModelFromStream(
                new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

        Map<String, String> elementoADepartamento = mapearElementosALanes(modelo);
        Map<String, Paso> pasosById = new LinkedHashMap<>();

        // 1. TAREAS (Task)
        // 1. USER TASKS (tareas humanas con formulario) — único tipo de tarea permitido
        for (UserTask task : modelo.getModelElementsByType(UserTask.class)) {
            Paso paso = crearPaso(task.getId(), task.getName(), "Tarea sin nombre",
                    TipoPaso.TAREA, elementoADepartamento, camposOriginales, responsablesOriginales,
                    camposVisiblesOriginales);
            pasosById.put(task.getId(), paso);
        }

        // 1.b. TASKS GENÉRICOS (retrocompat — los tratamos como UserTask)
        // Solo por si hay diagramas viejos o si el usuario arrastra un Task genérico
        // por error
        for (Task task : modelo.getModelElementsByType(Task.class)) {
            // Ignorar si ya fue procesado como UserTask (UserTask extends Task)
            if (pasosById.containsKey(task.getId()))
                continue;

            Paso paso = crearPaso(task.getId(), task.getName(), "Tarea sin nombre",
                    TipoPaso.TAREA, elementoADepartamento, camposOriginales, responsablesOriginales,
                    camposVisiblesOriginales);
            pasosById.put(task.getId(), paso);
        }

        // 2. GATEWAY EXCLUSIVO (Decisión)
        for (ExclusiveGateway gw : modelo.getModelElementsByType(ExclusiveGateway.class)) {
            Paso paso = crearPaso(gw.getId(), gw.getName(), "Decisión",
                    TipoPaso.GATEWAY_EXCLUSIVO, elementoADepartamento, camposOriginales, responsablesOriginales,
                    camposVisiblesOriginales);
            paso.setDepartamentoAsignadoId("SISTEMA");
            pasosById.put(gw.getId(), paso);
        }

        // 3. GATEWAY PARALELO (Bifurcación y Unión) 🆕
        for (ParallelGateway gw : modelo.getModelElementsByType(ParallelGateway.class)) {
            // Decidimos si es SPLIT (1 entrada, N salidas) o JOIN (N entradas, 1 salida)
            int entradas = gw.getIncoming().size();
            int salidas = gw.getOutgoing().size();

            TipoPaso tipo = (salidas > entradas) ? TipoPaso.GATEWAY_PARALELO_SPLIT
                    : TipoPaso.GATEWAY_PARALELO_JOIN;

            Paso paso = crearPaso(gw.getId(), gw.getName(),
                    tipo == TipoPaso.GATEWAY_PARALELO_SPLIT ? "Bifurcación Paralela" : "Unión Paralela",
                    tipo, elementoADepartamento, camposOriginales, responsablesOriginales, camposVisiblesOriginales);
            paso.setDepartamentoAsignadoId("SISTEMA");
            pasosById.put(gw.getId(), paso);
        }

        // 4. GATEWAY INCLUSIVO (opcional, activa los caminos que cumplen) 🆕
        for (InclusiveGateway gw : modelo.getModelElementsByType(InclusiveGateway.class)) {
            Paso paso = crearPaso(gw.getId(), gw.getName(), "Decisión Inclusiva",
                    TipoPaso.GATEWAY_INCLUSIVO, elementoADepartamento, camposOriginales, responsablesOriginales,
                    camposVisiblesOriginales);
            paso.setDepartamentoAsignadoId("SISTEMA");
            pasosById.put(gw.getId(), paso);
        }

        // 5. EVENTOS INTERMEDIOS (señales) 🆕
        for (IntermediateCatchEvent ev : modelo.getModelElementsByType(IntermediateCatchEvent.class)) {
            Paso paso = crearPaso(ev.getId(), ev.getName(), "Evento Intermedio",
                    TipoPaso.EVENTO_INTERMEDIO, elementoADepartamento, camposOriginales, responsablesOriginales,
                    camposVisiblesOriginales);
            paso.setDepartamentoAsignadoId("SISTEMA");
            pasosById.put(ev.getId(), paso);
        }

        // 6. TRANSICIONES (SequenceFlow)
        for (SequenceFlow flow : modelo.getModelElementsByType(SequenceFlow.class)) {
            FlowNode origen = flow.getSource();
            FlowNode destino = flow.getTarget();
            if (origen == null || destino == null)
                continue;

            // Si viene de StartEvent, marcar el paso destino como inicial
            if (origen instanceof StartEvent) {
                if (pasosById.containsKey(destino.getId())) {
                    definicion.setPasoInicialId(destino.getId());
                }
                continue;
            }

            Paso pasoOrigen = pasosById.get(origen.getId());
            if (pasoOrigen == null)
                continue;

            // Detectar si el destino es EndEvent normal o Terminate
            String destinoId;
            if (destino instanceof EndEvent) {
                destinoId = esTerminateEvent((EndEvent) destino) ? "FIN_TERMINA_TODO" : "FIN";
            } else {
                destinoId = destino.getId();
            }

            // Condición de la transición
            String condicion = determinarCondicion(origen, flow);

            // 👇 NUEVO: guardar también el nombre original de la flecha como nombreAccion
            String nombreOriginal = flow.getName() != null ? flow.getName().trim() : null;
            Transicion nuevaTransicion = new Transicion(condicion, destinoId);
            nuevaTransicion.setNombreAccion(nombreOriginal);
            pasoOrigen.getTransiciones().add(nuevaTransicion);
        }

        // Si no hay pasoInicial pero sí pasos, tomar el primero
        if (definicion.getPasoInicialId() == null && !pasosById.isEmpty()) {
            definicion.setPasoInicialId(pasosById.keySet().iterator().next());
        }

        // Detectar pasos que forman parte de un loop y marcarlos como re-ejecutables
        detectarLoops(pasosById);
        // 👇 NUEVO: Segundo pase — distinguir INICIO_CLIENTE vs SOLICITUD_CLIENTE
        // El paso del cliente que coincide con pasoInicialId es INICIO_CLIENTE
        // Los demás pasos del cliente son SOLICITUD_CLIENTE
        String pasoInicialId = definicion.getPasoInicialId();
        for (Paso p : pasosById.values()) {
            if ("PORTAL_WEB".equals(p.getDepartamentoAsignadoId())) {
                if (p.getId().equals(pasoInicialId)) {
                    p.setTipoResponsable(TipoResponsable.INICIO_CLIENTE);
                } else {
                    p.setTipoResponsable(TipoResponsable.SOLICITUD_CLIENTE);
                }
            }
        }
        definicion.setPasos(new ArrayList<>(pasosById.values()));
        return definicion;
    }

    /**
     * Determina la condición de una transición.
     * - Si viene de gateway exclusivo/inclusivo: usa el nombre del flow (APROBADO,
     * RECHAZADO, etc.)
     * - Si viene de paralelo: siempre es "PARALELO"
     * - Por defecto: APROBADO
     */
    private String determinarCondicion(FlowNode origen, SequenceFlow flow) {
        if (origen instanceof ParallelGateway) {
            return "PARALELO";
        }
        if (origen instanceof ExclusiveGateway || origen instanceof InclusiveGateway) {
            return (flow.getName() != null && !flow.getName().isBlank())
                    ? flow.getName().toUpperCase()
                    : "DEFAULT";
        }
        return (flow.getName() != null && !flow.getName().isBlank())
                ? flow.getName().toUpperCase()
                : "APROBADO";
    }

    /**
     * Detecta pasos que participan en un ciclo del grafo (iterativos).
     * Si un paso puede ser alcanzado desde sí mismo, se marca como re-ejecutable.
     */
    private void detectarLoops(Map<String, Paso> pasosById) {
        for (Paso paso : pasosById.values()) {
            if (puedeAlcanzarseDesdeSiMismo(paso.getId(), pasosById)) {
                paso.setPermiteReejecucion(true);
            }
        }
    }

    private boolean puedeAlcanzarseDesdeSiMismo(String origenId, Map<String, Paso> pasos) {
        Set<String> visitados = new HashSet<>();
        Deque<String> pila = new ArrayDeque<>();

        Paso origen = pasos.get(origenId);
        if (origen == null)
            return false;

        for (Transicion t : origen.getTransiciones()) {
            pila.push(t.getPasoDestinoId());
        }

        while (!pila.isEmpty()) {
            String actual = pila.pop();
            if (actual.equals(origenId))
                return true;
            if (visitados.contains(actual))
                continue;
            visitados.add(actual);

            Paso p = pasos.get(actual);
            if (p != null) {
                for (Transicion t : p.getTransiciones()) {
                    pila.push(t.getPasoDestinoId());
                }
            }
        }
        return false;
    }

    private boolean esTerminateEvent(EndEvent ev) {
        return ev.getEventDefinitions().stream()
                .anyMatch(def -> def instanceof TerminateEventDefinition);
    }

    private Paso crearPaso(String id, String nombre, String nombreDefault,
            TipoPaso tipo, Map<String, String> mapaDepartamentos,
            Map<String, List<CampoFormulario>> camposOriginales,
            Map<String, TipoResponsable> responsablesOriginales,
            Map<String, List<String>> camposVisiblesOriginales) {
        Paso paso = new Paso();
        paso.setId(id);
        paso.setNombre(nombre != null && !nombre.isBlank() ? nombre : nombreDefault);
        String deptoAsignado = mapaDepartamentos.getOrDefault(id, "SIN_ASIGNAR");
        paso.setDepartamentoAsignadoId(deptoAsignado);
        paso.setTipo(tipo);

        List<CampoFormulario> campos = camposOriginales.get(id);
        if (campos != null)
            paso.setCampos(campos);

        List<String> visibles = camposVisiblesOriginales.get(id);
        if (visibles != null) {
            paso.setCamposVisibles(visibles);
        }

        // 👇 NUEVO: si la lane es "Cliente" (PORTAL_WEB), auto-asignar tipoResponsable
        // La decisión INICIO_CLIENTE vs SOLICITUD_CLIENTE se hará en un segundo pase
        // (porque aquí todavía no sabemos si es el primer paso o no)
        if ("PORTAL_WEB".equals(deptoAsignado)) {
            // Temporal: marcamos como INICIO_CLIENTE, el segundo pase lo corrige si no es
            // inicial
            paso.setTipoResponsable(TipoResponsable.INICIO_CLIENTE);
        } else {
            TipoResponsable resp = responsablesOriginales.get(id);
            if (resp != null) {
                paso.setTipoResponsable(resp);
            } else {
                paso.setTipoResponsable(tipo == TipoPaso.TAREA
                        ? TipoResponsable.FUNCIONARIO
                        : TipoResponsable.AUTOMATICO);
            }
        }

        return paso;
    }

    /**
     * Mapea cada FlowNode (tarea, gateway, etc.) al ID del departamento de su lane.
     * CASO ESPECIAL: si la lane se llama "Cliente" o "Solicitante", se le asigna
     * el valor especial "PORTAL_WEB" para identificar que es rol virtual.
     */
    private Map<String, String> mapearElementosALanes(BpmnModelInstance modelo) {
        Map<String, String> mapa = new HashMap<>();
        Map<String, String> deptosPorNombre = new HashMap<>();
        for (Departamento d : departamentoRepository.findAll()) {
            deptosPorNombre.put(normalizar(d.getNombre()), d.getId());
        }

        for (Lane lane : modelo.getModelElementsByType(Lane.class)) {
            String nombreLane = lane.getName() != null ? lane.getName().trim() : lane.getId();
            String nombreNorm = normalizar(nombreLane);

            String valorFinal;
            // 👇 NUEVO: detectar lane especial "Cliente" / "Solicitante"
            if (esNombreCliente(nombreNorm)) {
                valorFinal = "PORTAL_WEB"; // identifica rol virtual
            } else {
                String deptoId = deptosPorNombre.get(nombreNorm);
                valorFinal = (deptoId != null) ? deptoId : "NO_EXISTE:" + nombreLane;
            }

            for (FlowNode nodo : lane.getFlowNodeRefs()) {
                mapa.put(nodo.getId(), valorFinal);
            }
        }
        return mapa;
    }

    /**
     * 👇 NUEVO: detecta si un nombre normalizado de lane representa al cliente
     */
    private boolean esNombreCliente(String nombreNormalizado) {
        if (nombreNormalizado == null)
            return false;
        return nombreNormalizado.equals("cliente")
                || nombreNormalizado.equals("solicitante")
                || nombreNormalizado.equals("cliente / solicitante")
                || nombreNormalizado.equals("cliente/solicitante");
    }

    private String normalizar(String s) {
        if (s == null)
            return "";
        return s.trim().toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n");
    }
}