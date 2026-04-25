package com.bpms.core.controllers;

import com.bpms.core.dto.auditoria.AuditoriaFiltroRequest;
import com.bpms.core.services.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 👇 NUEVO CU16: Controlador del Log de Auditoría.
 *
 * EXPONE EXCLUSIVAMENTE OPERACIONES DE LECTURA (GET y POST de consulta).
 * No hay PUT/DELETE → cualquier intento devolverá HTTP 405 Method Not Allowed
 * gracias a la regla en SecurityConfig (cumple Flujo Alternativo A1 del CU).
 *
 * Solo accesible para administradores (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditController {

    @Autowired
    private AuditService auditService;

    /**
     * Consulta paginada con filtros opcionales.
     *
     * Usamos POST porque la cantidad de filtros opcionales hace incómodo el GET
     * con query params, y el body permite tipado claro. NO modifica datos —
     * sigue siendo una operación de lectura.
     */
    @PostMapping("/consultar")
    public ResponseEntity<?> consultar(@RequestBody AuditoriaFiltroRequest filtros) {

        LocalDateTime desde = parsearFecha(filtros.getDesde());
        LocalDateTime hasta = parsearFecha(filtros.getHasta());

        int pagina = filtros.getPagina() != null ? filtros.getPagina() : 0;
        int tamano = filtros.getTamano() != null ? filtros.getTamano() : 50;

        Map<String, Object> resultado = auditService.consultar(
                filtros.getUsuarioId(),
                filtros.getCategoria(),
                filtros.getAccion(),
                filtros.getIpOrigen(),
                desde,
                hasta,
                filtros.getTextoLibre(),
                pagina,
                tamano
        );

        return ResponseEntity.ok(resultado);
    }

    /**
     * Devuelve listas distintas (usuarios, acciones, categorías) para poblar
     * los dropdowns del filtro en el frontend.
     */
    @GetMapping("/opciones-filtro")
    public ResponseEntity<?> opcionesFiltro() {
        return ResponseEntity.ok(auditService.obtenerOpcionesFiltro());
    }

    /**
     * Devuelve las categorías predefinidas del sistema (para el filtro).
     * Útil cuando aún no hay datos en algunas categorías y queremos mostrarlas igual.
     */
    @GetMapping("/categorias")
    public ResponseEntity<?> categorias() {
        return ResponseEntity.ok(java.util.List.of(
                AuditService.CAT_AUTH,
                AuditService.CAT_POLITICA,
                AuditService.CAT_USUARIO,
                AuditService.CAT_DEPARTAMENTO,
                AuditService.CAT_TRAMITE,
                AuditService.CAT_SISTEMA
        ));
    }

    /**
     * Parsea una fecha ISO-8601 ("2026-04-25T00:00:00") o null si viene vacía.
     */
    private LocalDateTime parsearFecha(String fechaIso) {
        if (fechaIso == null || fechaIso.isBlank()) return null;
        try {
            return LocalDateTime.parse(fechaIso, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            // Intento alterno: solo fecha sin hora ("2026-04-25")
            try {
                return LocalDateTime.parse(fechaIso + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e2) {
                return null;
            }
        }
    }
}