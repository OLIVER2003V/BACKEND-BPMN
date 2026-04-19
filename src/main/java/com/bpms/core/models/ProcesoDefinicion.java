package com.bpms.core.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "procesos_definicion")
public class ProcesoDefinicion {
    
    @Id
    private String id;
    
    private String codigo;
    private String nombre;
    private String descripcion;
    
    private boolean activo = true;
    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private LocalDateTime fechaUltimaActualizacion = LocalDateTime.now();
    
    private String pasoInicialId;
    private List<Paso> pasos = new ArrayList<>();
    
    // 👇 NUEVOS — para guardar el diagrama visual
    private String bpmnXml;
    private String svgPreview;

    public ProcesoDefinicion() {}

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaUltimaActualizacion() { return fechaUltimaActualizacion; }
    public void setFechaUltimaActualizacion(LocalDateTime f) { this.fechaUltimaActualizacion = f; }

    public String getPasoInicialId() { return pasoInicialId; }
    public void setPasoInicialId(String pasoInicialId) { this.pasoInicialId = pasoInicialId; }

    public List<Paso> getPasos() { return pasos; }
    public void setPasos(List<Paso> pasos) { this.pasos = pasos; }

    public String getBpmnXml() { return bpmnXml; }
    public void setBpmnXml(String bpmnXml) { this.bpmnXml = bpmnXml; }

    public String getSvgPreview() { return svgPreview; }
    public void setSvgPreview(String svgPreview) { this.svgPreview = svgPreview; }
}