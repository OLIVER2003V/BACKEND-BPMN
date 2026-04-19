package com.bpms.core.controllers;

import com.bpms.core.models.NuevoTramiteRequest;
import com.bpms.core.models.Tramite;
import com.bpms.core.repositories.TramiteRepository;
import com.bpms.core.services.FlujoService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.security.Principal;

@RestController
@RequestMapping("/api/tramites")
public class TramiteController {

    @Autowired
    private TramiteRepository tramiteRepository;

    // 1. Crear un trámite nuevo (Lo usará el CLIENTE)
    @PostMapping
    public Tramite crearTramite(@RequestBody Tramite tramite) {
        // Nos aseguramos de que nazca con la fecha exacta de hoy
        tramite.setFechaCreacion(LocalDateTime.now());
        tramite.setFechaUltimaActualizacion(LocalDateTime.now());
        return tramiteRepository.save(tramite);
    }

    // 2. Obtener trámites de una bandeja específica (Lo usará el FUNCIONARIO)
    @GetMapping("/bandeja/{departamentoId}")
    public List<Tramite> obtenerBandejaEntrada(@PathVariable String departamentoId) {
        return tramiteRepository.findByDepartamentoActualId(departamentoId);
    }

    // 3. Ver todos los trámites (Lo usará el ADMIN)
    @GetMapping
    public List<Tramite> obtenerTodos() {
        return tramiteRepository.findAll();
    }

    // 1. Método para buscar un trámite específico por su ID
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerTramitePorId(@PathVariable String id) {
        return tramiteRepository.findById(id)
                .map(tramite -> ResponseEntity.ok(tramite))
                .orElse(ResponseEntity.notFound().build());
    }

    @Autowired
    private FlujoService flujoService;
    // 2. Método para que el funcionario guarde su resolución (Actualizar)
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarTramite(@PathVariable String id, 
                                               @RequestBody Tramite tramiteActualizado, 
                                               Principal principal) { // Principal extrae al usuario del Token
        try {
            // Si hay un token válido, sacamos el username real. Si no, lo marcamos como SISTEMA
            String usernameFuncionario = (principal != null) ? principal.getName() : "SISTEMA";
            
            // Le pasamos la pelota a nuestro nuevo servicio experto
            Tramite tramiteProcesado = flujoService.procesarResolucion(id, tramiteActualizado, usernameFuncionario);
            
            return ResponseEntity.ok(tramiteProcesado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/historial")
    public ResponseEntity<?> obtenerHistorial(@PathVariable String id) {
        try {
            // Llamamos al servicio que acabamos de crear
            return ResponseEntity.ok(flujoService.obtenerHistorialTramite(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al obtener el historial: " + e.getMessage());
        }
    }

    // Buscar trámite público por Código de Seguimiento
    @GetMapping("/rastrear/{codigo}")
    public ResponseEntity<?> rastrearTramite(@PathVariable String codigo) {
        return tramiteRepository.findByCodigoSeguimiento(codigo)
                .map(tramite -> ResponseEntity.ok(tramite))
                .orElse(ResponseEntity.notFound().build());
    }
    // Importa NuevoTramiteRequest arriba si es necesario
    
    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciarTramite(@RequestBody NuevoTramiteRequest request) {
        try {
            Tramite nuevoTramite = flujoService.iniciarTramiteCliente(request);
            return ResponseEntity.ok(nuevoTramite);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al iniciar trámite: " + e.getMessage());
        }
    }

    // Endpoint para el Dashboard Gerencial
    @GetMapping("/dashboard/stats")
    public ResponseEntity<?> getDashboardStats() {
        try {
            long total = tramiteRepository.count();
            long aprobados = tramiteRepository.contarPorEstado("APROBADO");
            long rechazados = tramiteRepository.contarPorEstado("RECHAZADO");
            long enRevision = tramiteRepository.contarPorEstado("EN_REVISION");
            long enTiempo = tramiteRepository.contarPorEstado("EN_TIEMPO"); // Por si usas este estado

            // Sumamos los que están en proceso
            long enProceso = enRevision + enTiempo; 

            java.util.Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("total", total);
            stats.put("aprobados", aprobados);
            stats.put("rechazados", rechazados);
            stats.put("enProceso", enProceso);

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al cargar estadísticas");
        }
    }

    @GetMapping("/dashboard/por-politica")
public ResponseEntity<?> getStatsPorPolitica() {
    try {
        java.util.List<Tramite> todos = tramiteRepository.findAll();

        // Agrupar por procesoDefinicionId
        java.util.Map<String, java.util.Map<String, Long>> agrupado = new java.util.HashMap<>();

        for (Tramite t : todos) {
            String procId = t.getProcesoDefinicionId();
            if (procId == null) continue;

            agrupado.computeIfAbsent(procId, k -> new java.util.HashMap<>());
            java.util.Map<String, Long> stats = agrupado.get(procId);

            String estado = t.getEstadoSemaforo() != null ? t.getEstadoSemaforo().name() : "DESCONOCIDO";
            stats.merge("total", 1L, Long::sum);
            stats.merge(estado, 1L, Long::sum);
        }

        return ResponseEntity.ok(agrupado);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }
}
}