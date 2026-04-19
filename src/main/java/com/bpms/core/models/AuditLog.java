package com.bpms.core.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;
    
    // Sobre QUÉ documento se hizo la acción
    private String tramiteId; 
    
    // QUIÉN hizo la acción
    private String usuarioId; 
    
    // DESDE DÓNDE se hizo (para saber en qué departamento estaba cuando opinó)
    private String departamentoId; 
    
    // QUÉ ACCIÓN tomó (Ej: "APROBADO", "RECHAZADO", "CREADO", "DERIVADO")
    private String accion; 
    
    // QUÉ DIJO (El comentario o dictamen técnico)
    private String detalle; 
    
    // CUÁNDO ocurrió (Generado automáticamente por el servidor, imposible de falsificar por el cliente)
    private LocalDateTime fechaTimestamp = LocalDateTime.now();

    private java.util.Map<String, Object> datosFormulario;

    public AuditLog() {}

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTramiteId() { return tramiteId; }
    public void setTramiteId(String tramiteId) { this.tramiteId = tramiteId; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getDepartamentoId() { return departamentoId; }
    public void setDepartamentoId(String departamentoId) { this.departamentoId = departamentoId; }

    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }

    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }

    public LocalDateTime getFechaTimestamp() { return fechaTimestamp; }
    public void setFechaTimestamp(LocalDateTime fechaTimestamp) { this.fechaTimestamp = fechaTimestamp; }

    public java.util.Map<String, Object> getDatosFormulario() { return datosFormulario; }
    public void setDatosFormulario(java.util.Map<String, Object> datosFormulario) { this.datosFormulario = datosFormulario; }
}