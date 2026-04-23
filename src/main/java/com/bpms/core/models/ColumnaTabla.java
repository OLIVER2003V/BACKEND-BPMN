package com.bpms.core.models;

public class ColumnaTabla {
    private String id;
    private String etiqueta;
    private String tipo; // "texto", "numero", "fecha"

    public ColumnaTabla() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
}