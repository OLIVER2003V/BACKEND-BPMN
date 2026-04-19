package com.bpms.core.services;

import com.bpms.core.models.ProcesoDefinicion;
import com.bpms.core.repositories.ProcesoDefinicionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProcesoService {

    @Autowired
    private ProcesoDefinicionRepository procesoRepository;

    @Autowired
    private BpmnParserService bpmnParser;

    /**
     * Guarda un nuevo proceso. Si trae bpmnXml, lo parsea automáticamente
     * para generar pasos y transiciones.
     */
    public ProcesoDefinicion guardarProceso(ProcesoDefinicion proceso) {
        // Si trae XML, parseamos. Los pasos del frontend (con campos) se pasan
        // como argumento al parser para que los preserve.
        if (proceso.getBpmnXml() != null && !proceso.getBpmnXml().isBlank()) {
            bpmnParser.parsearYRellenar(proceso, proceso.getBpmnXml());
        }
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaUltimaActualizacion(LocalDateTime.now());
        proceso.setActivo(true);
        return procesoRepository.save(proceso);
    }

    public ProcesoDefinicion actualizarProceso(String id, ProcesoDefinicion datos) {
        ProcesoDefinicion existente = procesoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado: " + id));

        // Copiamos metadatos simples
        existente.setNombre(datos.getNombre());
        existente.setDescripcion(datos.getDescripcion());
        existente.setActivo(datos.isActivo());
        existente.setBpmnXml(datos.getBpmnXml());
        existente.setSvgPreview(datos.getSvgPreview());
        existente.setFechaUltimaActualizacion(LocalDateTime.now());

        // 👇 CLAVE: copiamos los pasos del frontend (con sus campos) ANTES del parseo
        // El parser los usará como fuente para preservar los campos definidos por el
        // admin
        if (datos.getPasos() != null) {
            existente.setPasos(datos.getPasos());
        }

        // Si cambió el XML, re-parseamos (el parser preserva los campos que acabamos de
        // copiar)
        if (datos.getBpmnXml() != null && !datos.getBpmnXml().isBlank()) {
            bpmnParser.parsearYRellenar(existente, datos.getBpmnXml());
        }

        return procesoRepository.save(existente);
    }

    public Optional<ProcesoDefinicion> obtenerPorId(String id) {
        return procesoRepository.findById(id);
    }

    public List<ProcesoDefinicion> obtenerTodos() {
        return procesoRepository.findAll();
    }

    public List<ProcesoDefinicion> obtenerActivos() {
        return procesoRepository.findAll().stream()
                .filter(ProcesoDefinicion::isActivo)
                .collect(Collectors.toList());
    }
}