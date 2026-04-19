package com.bpms.core.controllers;

import com.bpms.core.models.ProcesoDefinicion;
import com.bpms.core.services.ProcesoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                    p.getCampos().forEach(c -> 
                        System.out.println("      • " + c.getEtiqueta() + " (" + c.getTipo() + ")")
                    );
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
                    p.getCampos().forEach(c -> 
                        System.out.println("      • " + c.getEtiqueta() + " (" + c.getTipo() + ")")
                    );
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
    public ResponseEntity<?> listarPublicos() {
        return ResponseEntity.ok(procesoService.obtenerActivos());
    }
}