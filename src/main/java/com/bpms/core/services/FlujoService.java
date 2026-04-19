package com.bpms.core.services;

import com.bpms.core.models.AuditLog;
import com.bpms.core.models.NuevoTramiteRequest;
import com.bpms.core.models.Paso;
import com.bpms.core.models.ProcesoDefinicion;
import com.bpms.core.models.Tramite;
import com.bpms.core.models.Transicion;
import com.bpms.core.repositories.AuditLogRepository;
import com.bpms.core.repositories.ProcesoDefinicionRepository;
import com.bpms.core.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlujoService {

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ProcesoDefinicionRepository procesoRepository; // 👈 Agregamos el repositorio del Mapa

    public Tramite procesarResolucion(String tramiteId, Tramite datosActualizados, String usernameFuncionario) {

        Tramite tramiteExistente = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new RuntimeException("El expediente no existe"));

        String departamentoOrigenId = tramiteExistente.getDepartamentoActualId();
        String accionFuncionario = datosActualizados.getEstadoSemaforo().name(); // Ej: "APROBADO"

        // ------------------------------------------------------------------
        // 🚀 INICIO DEL PILOTO AUTOMÁTICO (BPM ENGINE)
        // ------------------------------------------------------------------

        // 1. Buscamos el mapa de carreteras
        // Cargamos el mapa según el ID guardado en el trámite (ya no hardcoded)
        String procesoId = tramiteExistente.getProcesoDefinicionId();
        if (procesoId == null) {
            throw new RuntimeException("Este trámite no está vinculado a ninguna política de negocio.");
        }
        ProcesoDefinicion mapa = procesoRepository.findById(procesoId)
                .orElseThrow(() -> new RuntimeException("La política asociada al trámite ya no existe."));

        // 2. Descubrimos en qué "Paso" estamos mirando el departamento actual
        Paso pasoActual = mapa.getPasos().stream()
                .filter(p -> p.getDepartamentoAsignadoId().equals(departamentoOrigenId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("El departamento actual no pertenece a este mapa"));

        // 3. Evaluamos la regla lógica: ¿Hacia dónde vamos si el usuario eligió esta
        // acción?
        Transicion reglaDeMovimiento = pasoActual.getTransiciones().stream()
                .filter(t -> t.getEstadoCondicion().equals(accionFuncionario))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "El mapa no tiene una regla definida para la acción: " + accionFuncionario));

        // 4. Calculamos el destino
        String destinoId = reglaDeMovimiento.getPasoDestinoId();
        String mensajeDerivacion = "";

        if (destinoId.equals("FIN")) {
            tramiteExistente.setDepartamentoActualId("ARCHIVADO");
            tramiteExistente.setPasoActualId("FIN"); // 👈 NUEVO
            mensajeDerivacion = "Proceso finalizado. Expediente archivado.";
        } else {
            Paso pasoDestino = mapa.getPasos().stream()
                    .filter(p -> p.getId().equals(destinoId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Paso destino no encontrado en el mapa"));

            tramiteExistente.setDepartamentoActualId(pasoDestino.getDepartamentoAsignadoId());
            tramiteExistente.setPasoActualId(pasoDestino.getId()); // 👈 NUEVO
            mensajeDerivacion = "Derivado automáticamente a: " + pasoDestino.getNombre();
        }

        // ------------------------------------------------------------------
        // 🛑 FIN DEL PILOTO AUTOMÁTICO
        // ------------------------------------------------------------------

        // Actualizamos los datos básicos del trámite
        tramiteExistente.setEstadoSemaforo(datosActualizados.getEstadoSemaforo());
        tramiteExistente.setDescripcion(datosActualizados.getDescripcion());
        tramiteExistente.setFechaUltimaActualizacion(LocalDateTime.now());
        // Ya NO usamos: datosActualizados.getDepartamentoActualId(). ¡El sistema decide
        // ahora!

        Tramite tramiteGuardado = tramiteRepository.save(tramiteExistente);

        // Clavamos la auditoría incluyendo el mensaje del piloto automático
        AuditLog log = new AuditLog();
        log.setTramiteId(tramiteGuardado.getId());
        log.setUsuarioId(usernameFuncionario);
        log.setDepartamentoId(departamentoOrigenId);
        log.setAccion(accionFuncionario);
        log.setDetalle("Dictamen emitido. " + mensajeDerivacion);
        log.setFechaTimestamp(LocalDateTime.now());

        log.setDatosFormulario(datosActualizados.getDatosFormulario());
        
        auditLogRepository.save(log);

        return tramiteGuardado;
    }

    public List<AuditLog> obtenerHistorialTramite(String tramiteId) {
        return auditLogRepository.findByTramiteIdOrderByFechaTimestampAsc(tramiteId);
    }

    // Método para cuando el CLIENTE inicia un trámite nuevo desde el Portal Web
    public Tramite iniciarTramiteCliente(NuevoTramiteRequest request) {

        // 1. Buscamos el mapa que el cliente eligió
        ProcesoDefinicion mapa = procesoRepository.findFirstByCodigo(request.getCodigoProceso())
                .orElseThrow(() -> new RuntimeException("El servicio solicitado no está disponible."));

        if (!mapa.isActivo()) {
            throw new RuntimeException("Este servicio está temporalmente inactivo.");
        }

        // 2. Descubrimos cuál es el Paso 1 de ese mapa
        String pasoInicialId = mapa.getPasoInicialId();
        if (pasoInicialId == null || pasoInicialId.isBlank()) {
            throw new RuntimeException("La política '" + mapa.getNombre() + "' no tiene paso inicial definido.");
        }

        Paso pasoInicial = mapa.getPasos().stream()
                .filter(p -> p.getId().equals(pasoInicialId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("El paso inicial no existe en la definición del proceso."));

        // 3. Creamos el expediente
        Tramite nuevoTramite = new Tramite();
        int numeroAleatorio = (int) (Math.random() * 9000) + 1000;
        nuevoTramite.setCodigoSeguimiento("TRM-" + LocalDateTime.now().getYear() + "-" + numeroAleatorio);
        nuevoTramite.setClienteId(request.getClienteId());
        nuevoTramite.setDescripcion(request.getDescripcion());

        // 👇 NUEVO: vinculamos el trámite con la política y el paso actual
        nuevoTramite.setProcesoDefinicionId(mapa.getId());
        nuevoTramite.setPasoActualId(pasoInicial.getId());
        nuevoTramite.setDepartamentoActualId(pasoInicial.getDepartamentoAsignadoId());

        nuevoTramite.setEstadoSemaforo(com.bpms.core.models.EstadoTramite.EN_REVISION);
        nuevoTramite.setFechaCreacion(LocalDateTime.now());
        nuevoTramite.setFechaUltimaActualizacion(LocalDateTime.now());

        Tramite tramiteGuardado = tramiteRepository.save(nuevoTramite);

        // 4. Auditoría inicial
        AuditLog log = new AuditLog();
        log.setTramiteId(tramiteGuardado.getId());
        log.setUsuarioId(request.getClienteId());
        log.setDepartamentoId("PORTAL_WEB");
        log.setAccion("INICIADO");
        log.setDetalle("El cliente inició la solicitud '" + mapa.getNombre()
                + "'. Asignado automáticamente a: " + pasoInicial.getNombre());
        log.setFechaTimestamp(LocalDateTime.now());
        auditLogRepository.save(log);

        return tramiteGuardado;
    }
}