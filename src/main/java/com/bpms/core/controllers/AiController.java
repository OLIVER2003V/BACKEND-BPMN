package com.bpms.core.controllers;

import com.bpms.core.dto.ia.FlujoGeneradoResponse;
import com.bpms.core.services.AuditService;
import com.bpms.core.services.GeminiAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.bpms.core.dto.ia.ChatbotResponse;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ia")
public class AiController {

    @Autowired
    private GeminiAiService aiService;

    // 👇 NUEVO CU17: para auditar el uso del copiloto
    @Autowired
    private AuditService auditService;

    @PostMapping("/sugerir")
    public ResponseEntity<?> sugerirRespuesta(@RequestBody Map<String, Object> payload) {
        try {
            String contexto = (String) payload.get("contexto");
            String descripcion = (String) payload.get("descripcion");
            List<String> archivos = (List<String>) payload.get("archivos");

            String sugerenciaJson = aiService.generarSugerencia(contexto, descripcion, archivos);
            return ResponseEntity.ok(Map.of("texto", sugerenciaJson.trim()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", "Asistente no disponible: " + e.getMessage()));
        }
    }

    /**
     * 👇 NUEVO CU17: Endpoint refactorizado — devuelve flujo validado + advertencias.
     * Cumple Flujo A1 del CU: si la IA no entiende, retorna 422 con mensaje específico.
     */
    @PostMapping("/generar-flujo")
    public ResponseEntity<?> generarFlujo(@RequestBody Map<String, Object> payload) {
        String promptAdmin = (String) payload.get("prompt");
        String deptosDisp = (String) payload.get("departamentosDisponibles");

        if (promptAdmin == null || promptAdmin.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "El prompt no puede estar vacío",
                    "tipo", "PROMPT_VACIO"
            ));
        }

        try {
            FlujoGeneradoResponse response = aiService.generarFlujoBpmn(promptAdmin, deptosDisp);

            // 👇 CU16: auditar uso del copiloto IA
            auditService.registrar(
                    actorActual(),
                    AuditService.CAT_POLITICA,
                    "IA_FLUJO_GENERADO",
                    "Copiloto IA generó flujo con " + response.getTotalNodos() + " nodos y "
                            + response.getTotalConexiones() + " conexiones. Prompt: \""
                            + (promptAdmin.length() > 100 ? promptAdmin.substring(0, 100) + "..." : promptAdmin) + "\""
            );

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";

            // Flujo A1: IA no pudo procesar la solicitud
            if (msg.startsWith("FLUJO_INCOHERENTE")) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                        "error", "La IA no pudo procesar la solicitud. Por favor, sea más específico con los departamentos involucrados y las acciones del flujo.",
                        "tipo", "FLUJO_INCOHERENTE",
                        "detalle", msg
                ));
            }

            // IA saturada
            if (msg.startsWith("IA_SATURADA")) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "error", "La IA está temporalmente saturada. Intenta en unos minutos.",
                        "tipo", "IA_SATURADA"
                ));
            }

            // Otro error
            System.err.println("Error generando flujo con IA: " + msg);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Error inesperado al generar el flujo: " + msg,
                    "tipo", "ERROR_INTERNO"
            ));
        }
    }

    /**
     * 👇 NUEVO Asistente IA Cliente: chat conversacional.
     * Personaliza la respuesta usando el username actual (clienteId) y mantiene
     * dominio acotado a trámites de la institución.
     */
    @PostMapping("/chatbot-cliente")
    public ResponseEntity<?> chatbotCliente(@RequestBody Map<String, Object> payload) {
        try {
            String mensaje = (String) payload.get("mensaje");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> historial = (List<Map<String, String>>) payload.get("historial");

            if (mensaje == null || mensaje.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "El mensaje no puede estar vacío",
                        "tipo", "MENSAJE_VACIO"
                ));
            }

            String clienteId = actorActual();
            ChatbotResponse response = aiService.chatbotCliente(mensaje, historial, clienteId);

            // 👇 CU16: auditar consulta al asistente IA
            try {
                String preview = mensaje.length() > 80 ? mensaje.substring(0, 80) + "..." : mensaje;
                auditService.registrar(
                        clienteId,
                        AuditService.CAT_SISTEMA,
                        "IA_CHATBOT_CONSULTA",
                        "Consulta al asistente IA: \"" + preview + "\""
                );
            } catch (Exception ignored) { /* no romper el flujo del chat por error de audit */ }

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";

            if (msg.startsWith("IA_SATURADA")) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "error", "El asistente IA está temporalmente saturado. Intenta de nuevo en unos segundos.",
                        "tipo", "IA_SATURADA"
                ));
            }

            System.err.println("Error en chatbot cliente: " + msg);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "El asistente no está disponible: " + msg,
                    "tipo", "ERROR_INTERNO"
            ));
        }
    }

    private String actorActual() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String name = auth.getName();
                return (name != null && !name.equals("anonymousUser")) ? name : "SISTEMA";
            }
        } catch (Exception ignored) {}
        return "SISTEMA";
    }
}