package com.bpms.core.services;

import com.bpms.core.models.CampoFormulario;
import com.bpms.core.models.Departamento;
import com.bpms.core.models.Paso;
import com.bpms.core.models.ProcesoDefinicion;
import com.bpms.core.models.Transicion;
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

        // 👇 PASO CRÍTICO: guardar los campos originales ANTES de hacer cualquier cosa
        // Mapa: pasoId → lista de campos definidos por el admin en el frontend
        Map<String, List<CampoFormulario>> camposOriginales = new HashMap<>();
        if (definicion.getPasos() != null) {
            for (Paso pasoOriginal : definicion.getPasos()) {
                if (pasoOriginal.getId() != null && pasoOriginal.getCampos() != null) {
                    camposOriginales.put(pasoOriginal.getId(), pasoOriginal.getCampos());
                }
            }
        }
        System.out.println("🔧 Campos originales recibidos del frontend: " + camposOriginales);

        BpmnModelInstance modelo = Bpmn.readModelFromStream(
                new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

        Map<String, String> elementoADepartamento = mapearElementosALanes(modelo);

        // 1. Procesar Tasks como Pasos
        Collection<Task> tasks = modelo.getModelElementsByType(Task.class);
        Map<String, Paso> pasosById = new LinkedHashMap<>();

        for (Task task : tasks) {
            Paso paso = new Paso();
            paso.setId(task.getId());
            paso.setNombre(task.getName() != null ? task.getName() : "Tarea sin nombre");
            paso.setDepartamentoAsignadoId(
                    elementoADepartamento.getOrDefault(task.getId(), "SIN_ASIGNAR"));

            // 👇 Restauramos los campos definidos por el admin
            List<CampoFormulario> campos = camposOriginales.get(task.getId());
            if (campos != null) {
                paso.setCampos(campos);
            }

            pasosById.put(task.getId(), paso);
        }

        // 2. Procesar Gateways exclusivos como pasos virtuales
        Collection<ExclusiveGateway> gateways = modelo.getModelElementsByType(ExclusiveGateway.class);
        for (ExclusiveGateway gw : gateways) {
            Paso paso = new Paso();
            paso.setId(gw.getId());
            paso.setNombre(gw.getName() != null ? gw.getName() : "Decisión");
            paso.setDepartamentoAsignadoId(
                    elementoADepartamento.getOrDefault(gw.getId(), "SISTEMA"));

            // Los gateways también pueden tener campos (aunque raro)
            List<CampoFormulario> campos = camposOriginales.get(gw.getId());
            if (campos != null) {
                paso.setCampos(campos);
            }

            pasosById.put(gw.getId(), paso);
        }

        // 3. Procesar SequenceFlows como Transiciones
        Collection<SequenceFlow> flows = modelo.getModelElementsByType(SequenceFlow.class);
        for (SequenceFlow flow : flows) {
            FlowNode origen = flow.getSource();
            FlowNode destino = flow.getTarget();
            if (origen == null || destino == null)
                continue;

            if (origen instanceof StartEvent) {
                if (pasosById.containsKey(destino.getId())) {
                    definicion.setPasoInicialId(destino.getId());
                }
                continue;
            }

            Paso pasoOrigen = pasosById.get(origen.getId());
            if (pasoOrigen == null)
                continue;

            String destinoId = (destino instanceof EndEvent) ? "FIN" : destino.getId();

            String condicion;
            if (origen instanceof ExclusiveGateway) {
                condicion = (flow.getName() != null && !flow.getName().isBlank())
                        ? flow.getName().toUpperCase()
                        : "DEFAULT";
            } else {
                condicion = (flow.getName() != null && !flow.getName().isBlank())
                        ? flow.getName().toUpperCase()
                        : "APROBADO";
            }

            pasoOrigen.getTransiciones().add(new Transicion(condicion, destinoId));
        }

        if (definicion.getPasoInicialId() == null && !pasosById.isEmpty()) {
            definicion.setPasoInicialId(pasosById.keySet().iterator().next());
        }

        definicion.setPasos(new ArrayList<>(pasosById.values()));
        System.out.println("🔧 Pasos finales con campos: " +
                pasosById.values().stream()
                        .map(p -> p.getId() + "=" + (p.getCampos() != null ? p.getCampos().size() : 0))
                        .toList());

        return definicion;
    }

    /**
     * Mapea cada nodo a su departamento por NOMBRE de la lane.
     * Busca el departamento en la BD por nombre (case-insensitive, trimmed).
     * Si no existe, deja "SIN_ASIGNAR" como marcador.
     */
    private Map<String, String> mapearElementosALanes(BpmnModelInstance modelo) {
        Map<String, String> mapa = new HashMap<>();
        Collection<Lane> lanes = modelo.getModelElementsByType(Lane.class);

        // Cache de departamentos: nombreNormalizado → id
        Map<String, String> deptosPorNombre = new HashMap<>();
        for (Departamento d : departamentoRepository.findAll()) {
            deptosPorNombre.put(normalizar(d.getNombre()), d.getId());
        }

        for (Lane lane : lanes) {
            String nombreLane = lane.getName() != null ? lane.getName().trim() : lane.getId();
            String deptoId = deptosPorNombre.get(normalizar(nombreLane));

            // Si no existe departamento, guardamos el nombre con marcador para diagnóstico
            String valorFinal = (deptoId != null) ? deptoId : "NO_EXISTE:" + nombreLane;

            for (FlowNode nodo : lane.getFlowNodeRefs()) {
                mapa.put(nodo.getId(), valorFinal);
            }
        }
        return mapa;
    }

    private String normalizar(String s) {
        if (s == null)
            return "";
        return s.trim().toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n");
    }
}