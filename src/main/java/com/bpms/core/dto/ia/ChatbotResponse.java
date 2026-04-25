package com.bpms.core.dto.ia;

import java.util.List;

// 👇 NUEVO (Asistente IA Cliente): payload de respuesta del chatbot
public class ChatbotResponse {
    private String respuesta;
    private List<String> sugerenciasRapidas;
    private String advertencia; // opcional, si la IA no pudo o salió de tema

    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
    public List<String> getSugerenciasRapidas() { return sugerenciasRapidas; }
    public void setSugerenciasRapidas(List<String> sugerenciasRapidas) { this.sugerenciasRapidas = sugerenciasRapidas; }
    public String getAdvertencia() { return advertencia; }
    public void setAdvertencia(String advertencia) { this.advertencia = advertencia; }
}