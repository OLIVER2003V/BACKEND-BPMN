package com.bpms.core.controllers;

import com.bpms.core.models.EstadoProceso;
import com.bpms.core.models.ProcesoDefinicion;
import com.bpms.core.services.ProcesoService;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/procesos")
public class ProcesoController {

    @Autowired
    private ProcesoService procesoService;

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody ProcesoDefinicion proceso) {
        // 👇 DEBUG: imprimir lo que llegó
        System.out.println("📦 POST recibido:");
        if (proceso.getPasos() != null) {
            proceso.getPasos().forEach(p -> {
                int numCampos = p.getCampos() != null ? p.getCampos().size() : 0;
                System.out.println("  - " + p.getId() + " | nombre: " + p.getNombre() + " | campos: " + numCampos);
                if (p.getCampos() != null) {
                    p.getCampos()
                            .forEach(c -> System.out.println("      • " + c.getEtiqueta() + " (" + c.getTipo() + ")"));
                }
            });
        }

        try {
            return ResponseEntity.ok(procesoService.guardarProceso(proceso));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error al guardar: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(procesoService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable String id) {
        return procesoService.obtenerPorId(id)
                .map(p -> ResponseEntity.ok((Object) p))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable String id, @RequestBody ProcesoDefinicion proceso) {
        // 👇 DEBUG: imprimir lo que llegó
        System.out.println("📦 PUT recibido para id=" + id + ":");
        if (proceso.getPasos() != null) {
            proceso.getPasos().forEach(p -> {
                int numCampos = p.getCampos() != null ? p.getCampos().size() : 0;
                System.out.println("  - " + p.getId() + " | nombre: " + p.getNombre() + " | campos: " + numCampos);
                if (p.getCampos() != null) {
                    p.getCampos()
                            .forEach(c -> System.out.println("      • " + c.getEtiqueta() + " (" + c.getTipo() + ")"));
                }
            });
        }

        try {
            return ResponseEntity.ok(procesoService.actualizarProceso(id, proceso));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/publicos")
    public ResponseEntity<List<ProcesoDefinicion>> obtenerProcesosPublicos() {
        // 👇 NUEVO: solo devolver procesos publicados (ACTIVOS)
        List<ProcesoDefinicion> activos = procesoService.obtenerPorEstado(EstadoProceso.ACTIVA);
        return ResponseEntity.ok(activos);
    }

    /**
     * POST /api/admin/procesos/{id}/publicar
     * Publica una política en borrador (admin only)
     */

    @PostMapping("/{id}/publicar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> publicarPolitica(@PathVariable String id, Authentication auth) {
        try {
            String username = auth.getName();
            ProcesoDefinicion publicado = procesoService.publicar(id, username);
            return ResponseEntity.ok(publicado);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/admin/procesos/{id}/nueva-version
     * Crea un nuevo borrador a partir de una política publicada
     */
    @PostMapping("/{id}/nueva-version")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> crearNuevaVersion(@PathVariable String id, Authentication auth) {
        try {
            String username = auth.getName();
            ProcesoDefinicion nueva = procesoService.crearNuevaVersion(id, username);
            return ResponseEntity.ok(nueva);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/admin/procesos/{id}/validar
     * Valida la integridad sin publicar. Útil para mostrar errores al admin.
     */
    @PostMapping("/{id}/validar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> validarPolitica(@PathVariable String id) {
        return procesoService.obtenerPorId(id)
                .map(proceso -> {
                    List<String> errores = procesoService.validarIntegridad(proceso);
                    return ResponseEntity.ok((Object) Map.of(
                            "valido", errores.isEmpty(),
                            "errores", errores));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/admin/procesos/{codigoBase}/versiones
     * Obtiene el historial de versiones de una política
     */
    @GetMapping("/versiones/{codigoBase}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProcesoDefinicion>> obtenerVersiones(@PathVariable String codigoBase) {
        return ResponseEntity.ok(procesoService.obtenerHistorialVersiones(codigoBase));
    }
}