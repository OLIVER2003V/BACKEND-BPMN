package com.bpms.core.models;

public class Transicion {
    private String estadoCondicion; // Ej: "APROBADO", "RECHAZADO"
    private String pasoDestinoId;   // El ID del siguiente paso en el flujo

    public Transicion() {}

    public Transicion(String estadoCondicion, String pasoDestinoId) {
        this.estadoCondicion = estadoCondicion;
        this.pasoDestinoId = pasoDestinoId;
    }

    public String getEstadoCondicion() { return estadoCondicion; }
    public void setEstadoCondicion(String estadoCondicion) { this.estadoCondicion = estadoCondicion; }

    public String getPasoDestinoId() { return pasoDestinoId; }
    public void setPasoDestinoId(String pasoDestinoId) { this.pasoDestinoId = pasoDestinoId; }
}