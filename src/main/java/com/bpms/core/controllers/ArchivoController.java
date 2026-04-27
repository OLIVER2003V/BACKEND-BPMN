package com.bpms.core.controllers;

import com.bpms.core.services.ArchivoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Controller delgado: recibe la petición HTTP y delega TODO al ArchivoService.
 * 👇 NUEVO: Refactorizado para usar AWS S3 (antes guardaba en filesystem de EC2).
 *
 * NOTA: Los endpoints `/ver/...` se eliminaron porque ahora las URLs son directas
 * a S3 (https://bpms-core-archivos-oliver.s3.us-east-2.amazonaws.com/...).
 * El frontend ya no necesita pasar por el backend para descargar archivos.
 */
@RestController
@RequestMapping("/api/archivos")
public class ArchivoController {

    private final ArchivoService archivoService;

    public ArchivoController(ArchivoService archivoService) {
        this.archivoService = archivoService;
    }

    @PostMapping("/subir")
    public ResponseEntity<?> subirArchivo(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "tramiteId", required = false) String tramiteId) {
        try {
            Map<String, Object> resp = archivoService.subirArchivo(archivo, tramiteId);
            return ResponseEntity.ok(resp);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al subir archivo: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error inesperado: " + e.getMessage()));
        }
    }

    /**
     * 👇 NUEVO: Endpoint para que el frontend pueda eliminar archivos
     * cuando el usuario quita un campo de archivo del formulario.
     */
    @DeleteMapping("/eliminar")
    public ResponseEntity<?> eliminarArchivo(@RequestParam("url") String url) {
        try {
            archivoService.eliminarArchivo(url);
            return ResponseEntity.ok(Map.of("mensaje", "Archivo eliminado"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}