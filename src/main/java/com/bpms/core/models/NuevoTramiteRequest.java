package com.bpms.core.models; // O com.bpms.core.dto si creaste la carpeta

public class NuevoTramiteRequest {
    private String codigoProceso; // Ej: "LIC-COM-02"
    private String clienteId;     // Ej: "cliente3"
    private String descripcion;   // El relato del ciudadano

    // Getters y Setters
    public String getCodigoProceso() { return codigoProceso; }
    public void setCodigoProceso(String codigoProceso) { this.codigoProceso = codigoProceso; }
    
    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }
    
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}