package com.bpms.core.models;

/**
 * Define un campo de formulario dentro de un Paso.
 * El admin lo configura al crear la política; el funcionario lo llena al atender el trámite.
 */
public class CampoFormulario {
    private String id;          // identificador único dentro del paso (ej: "docs_ok")
    private String etiqueta;    // texto visible (ej: "¿Documentos completos?")
    private String tipo;        // "texto" | "numero" | "textarea" | "si_no" | "fecha" | "seleccion"
    private boolean requerido;
    private String opciones;    // para "seleccion": valores separados por coma

    public CampoFormulario() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public boolean isRequerido() { return requerido; }
    public void setRequerido(boolean requerido) { this.requerido = requerido; }

    public String getOpciones() { return opciones; }
    public void setOpciones(String opciones) { this.opciones = opciones; }
}