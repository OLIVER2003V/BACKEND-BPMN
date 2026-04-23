package com.bpms.core.controllers;

import com.bpms.core.models.Departamento;
import com.bpms.core.repositories.DepartamentoRepository;
import com.bpms.core.repositories.UsuarioRepository;
import com.bpms.core.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/departamentos")
public class DepartamentoController {

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TramiteRepository tramiteRepository;

    @GetMapping
    public List<Departamento> obtenerTodos() {
        return departamentoRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable String id) {
        return departamentoRepository.findById(id)
                .map(d -> ResponseEntity.ok((Object) d))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ResponseEntity<?> obtenerEstadisticas() {
        List<Departamento> todos = departamentoRepository.findAll();
        Map<String, Map<String, Object>> stats = new HashMap<>();

        for (Departamento d : todos) {
            Map<String, Object> deptoStats = new HashMap<>();

            // Contar funcionarios asignados
            long funcionarios = usuarioRepository.findAll().stream()
                    .filter(u -> d.getId().equals(u.getDepartamentoId()))
                    .count();

            // Contar trámites actualmente en este depto
            long tramitesActivos = tramiteRepository.findAll().stream()
                    .filter(t -> d.getId().equals(t.getDepartamentoActualId()))
                    .count();

            deptoStats.put("funcionarios", funcionarios);
            deptoStats.put("tramitesActivos", tramitesActivos);
            stats.put(d.getId(), deptoStats);
        }

        return ResponseEntity.ok(stats);
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Departamento departamento) {
        // Validar nombre único
        if (departamentoRepository.findByNombre(departamento.getNombre()).isPresent()) {
            return ResponseEntity.badRequest().body("Ya existe un departamento con ese nombre");
        }

        departamento.setFechaCreacion(LocalDateTime.now());
        departamento.setActivo(true);
        return ResponseEntity.ok(departamentoRepository.save(departamento));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable String id, @RequestBody Departamento datos) {
        return departamentoRepository.findById(id)
                .map(existente -> {
                    // Si cambia el nombre, validar que no choque con otro
                    if (datos.getNombre() != null && !datos.getNombre().equals(existente.getNombre())) {
                        if (departamentoRepository.findByNombre(datos.getNombre()).isPresent()) {
                            return ResponseEntity.badRequest().body((Object) "Ya existe un departamento con ese nombre");
                        }
                        existente.setNombre(datos.getNombre());
                    }

                    if (datos.getDescripcion() != null) existente.setDescripcion(datos.getDescripcion());
                    existente.setActivo(datos.isActivo());

                    return ResponseEntity.ok((Object) departamentoRepository.save(existente));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/toggle-activo")
    public ResponseEntity<?> toggleActivo(@PathVariable String id) {
        return departamentoRepository.findById(id)
                .map(d -> {
                    d.setActivo(!d.isActivo());
                    return ResponseEntity.ok((Object) departamentoRepository.save(d));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable String id) {
        if (!departamentoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        // Validar que no tenga funcionarios asignados
        long funcionarios = usuarioRepository.findAll().stream()
                .filter(u -> id.equals(u.getDepartamentoId()))
                .count();

        if (funcionarios > 0) {
            return ResponseEntity.badRequest().body(
                "No se puede eliminar: el departamento tiene " + funcionarios +
                " funcionario(s) asignado(s). Reasigna o elimina los funcionarios primero."
            );
        }

        // Validar que no tenga trámites activos
        long tramitesActivos = tramiteRepository.findAll().stream()
                .filter(t -> id.equals(t.getDepartamentoActualId()))
                .count();

        if (tramitesActivos > 0) {
            return ResponseEntity.badRequest().body(
                "No se puede eliminar: el departamento tiene " + tramitesActivos +
                " trámite(s) activo(s). Considera desactivarlo en lugar de eliminarlo."
            );
        }

        departamentoRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("mensaje", "Departamento eliminado correctamente"));
    }
}