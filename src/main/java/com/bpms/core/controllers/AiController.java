package com.bpms.core.controllers;

import com.bpms.core.services.GeminiAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ia")
public class AiController {

    @Autowired
    private GeminiAiService aiService;

    @PostMapping("/sugerir")
    public ResponseEntity<?> sugerirRespuesta(@RequestBody Map<String, Object> payload) {
        try {
            String contexto = (String) payload.get("contexto");
            String descripcion = (String) payload.get("descripcion");
            List<String> archivos = (List<String>) payload.get("archivos");

            // 👇 NUEVO: Ya no pasamos 'nombreCampo'
            String sugerenciaJson = aiService.generarSugerencia(contexto, descripcion, archivos);
            
            return ResponseEntity.ok(Map.of("texto", sugerenciaJson.trim()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", "Asistente no disponible: " + e.getMessage()));
        }
    }
}