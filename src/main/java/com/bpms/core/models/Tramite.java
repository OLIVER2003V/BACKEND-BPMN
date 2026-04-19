package com.bpms.core.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Document(collection = "tramites")
public class Tramite {

    @Id
    private String id;

    private String codigoSeguimiento;
    private String descripcion;

    private String clienteId;
    private String procesoDefinicionId; // 👈 NUEVO — qué política sigue
    private String pasoActualId; // 👈 NUEVO — id del paso actual dentro de la política
    private String departamentoActualId;
    private String responsableActualId;

    private EstadoTramite estadoSemaforo;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUltimaActualizacion;

    public Tramite() {
        this.fechaCreacion = LocalDateTime.now();
        this.fechaUltimaActualizacion = LocalDateTime.now();
        this.estadoSemaforo = EstadoTramite.EN_TIEMPO;
    }

    @org.springframework.data.annotation.Transient
    private java.util.Map<String, Object> datosFormulario;

    public java.util.Map<String, Object> getDatosFormulario() {
        return datosFormulario;
    }

    public void setDatosFormulario(java.util.Map<String, Object> datos) {
        this.datosFormulario = datos;
    }
}