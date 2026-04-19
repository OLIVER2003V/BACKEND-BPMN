package com.bpms.core.models;

import java.util.ArrayList;
import java.util.List;

public class Paso {
    private String id;
    private String nombre;
    private String departamentoAsignadoId;
    private List<Transicion> transiciones = new ArrayList<>();
    
    // 👇 NUEVO: definición de campos del formulario de este paso
    private List<CampoFormulario> campos = new ArrayList<>();

    public Paso() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDepartamentoAsignadoId() { return departamentoAsignadoId; }
    public void setDepartamentoAsignadoId(String departamentoAsignadoId) { this.departamentoAsignadoId = departamentoAsignadoId; }

    public List<Transicion> getTransiciones() { return transiciones; }
    public void setTransiciones(List<Transicion> transiciones) { this.transiciones = transiciones; }

    public List<CampoFormulario> getCampos() { return campos; }
    public void setCampos(List<CampoFormulario> campos) { this.campos = campos; }
}