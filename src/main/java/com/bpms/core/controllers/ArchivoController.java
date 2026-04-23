package com.bpms.core.controllers;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/archivos")
public class ArchivoController {

    private static final String CARPETA_BASE = "uploads";

    @PostMapping("/subir")
    public ResponseEntity<?> subirArchivo(@RequestParam("archivo") MultipartFile archivo,
                                           @RequestParam(value = "tramiteId", required = false) String tramiteId) {
        if (archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Archivo vacío"));
        }

        try {
            // Crear carpeta si no existe
            Path carpeta = Paths.get(CARPETA_BASE);
            if (!Files.exists(carpeta)) Files.createDirectories(carpeta);

            // Subcarpeta por trámite (opcional)
            if (tramiteId != null && !tramiteId.isBlank()) {
                carpeta = carpeta.resolve(tramiteId);
                if (!Files.exists(carpeta)) Files.createDirectories(carpeta);
            }

            // Nombre único (evitar colisiones)
            String nombreOriginal = archivo.getOriginalFilename();
            String extension = "";
            if (nombreOriginal != null && nombreOriginal.contains(".")) {
                extension = nombreOriginal.substring(nombreOriginal.lastIndexOf("."));
            }
            String nombreUnico = UUID.randomUUID() + extension;

            Path destino = carpeta.resolve(nombreUnico);
            archivo.transferTo(destino);

            // Retornar URL pública para acceder
            String urlPublica = "/api/archivos/ver/" +
                    (tramiteId != null ? tramiteId + "/" : "") + nombreUnico;

            return ResponseEntity.ok(Map.of(
                    "nombreOriginal", nombreOriginal,
                    "nombreAlmacenado", nombreUnico,
                    "url", urlPublica,
                    "tamano", archivo.getSize(),
                    "fechaSubida", LocalDateTime.now().toString()
            ));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Error al guardar archivo: " + e.getMessage()));
        }
    }

    @GetMapping("/ver/{nombre}")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable String nombre) {
        return servirArchivo(Paths.get(CARPETA_BASE, nombre));
    }

    @GetMapping("/ver/{tramiteId}/{nombre}")
    public ResponseEntity<Resource> descargarArchivoDeTramite(
            @PathVariable String tramiteId, @PathVariable String nombre) {
        return servirArchivo(Paths.get(CARPETA_BASE, tramiteId, nombre));
    }

    private ResponseEntity<Resource> servirArchivo(Path ruta) {
        try {
            if (!Files.exists(ruta)) return ResponseEntity.notFound().build();

            Resource resource = new UrlResource(ruta.toUri());
            String contentType = Files.probeContentType(ruta);
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + ruta.getFileName() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}