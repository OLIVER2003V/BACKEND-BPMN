package com.bpms.core.services;

import com.bpms.core.models.AuditLog;
import com.bpms.core.models.EstadoProceso;
import com.bpms.core.models.Paso;
import com.bpms.core.models.ProcesoDefinicion;
import com.bpms.core.models.TipoPaso;
import com.bpms.core.models.Transicion;
import com.bpms.core.repositories.AuditLogRepository;
import com.bpms.core.repositories.ProcesoDefinicionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProcesoService {

    @Autowired
    private ProcesoDefinicionRepository procesoRepository;

    @Autowired
    private BpmnParserService bpmnParser;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Guarda un nuevo proceso. Si trae bpmnXml, lo parsea automáticamente
     * para generar pasos y transiciones.
     */
    public ProcesoDefinicion guardarProceso(ProcesoDefinicion proceso) {
        // Si trae XML, parseamos. Los pasos del frontend (con campos) se pasan
        // como argumento al parser para que los preserve.
        if (proceso.getBpmnXml() != null && !proceso.getBpmnXml().isBlank()) {
            bpmnParser.parsearYRellenar(proceso, proceso.getBpmnXml());
        }
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaUltimaActualizacion(LocalDateTime.now());
        proceso.setActivo(true);
        return procesoRepository.save(proceso);
    }

    public ProcesoDefinicion actualizarProceso(String id, ProcesoDefinicion datos) {
        ProcesoDefinicion existente = procesoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado: " + id));

        // Copiamos metadatos simples
        existente.setNombre(datos.getNombre());
        existente.setDescripcion(datos.getDescripcion());
        existente.setActivo(datos.isActivo());
        existente.setBpmnXml(datos.getBpmnXml());
        existente.setSvgPreview(datos.getSvgPreview());
        existente.setFechaUltimaActualizacion(LocalDateTime.now());

        // 👇 CLAVE: copiamos los pasos del frontend (con sus campos) ANTES del parseo
        // El parser los usará como fuente para preservar los campos definidos por el
        // admin
        if (datos.getPasos() != null) {
            existente.setPasos(datos.getPasos());
        }

        // Si cambió el XML, re-parseamos (el parser preserva los campos que acabamos de
        // copiar)
        if (datos.getBpmnXml() != null && !datos.getBpmnXml().isBlank()) {
            bpmnParser.parsearYRellenar(existente, datos.getBpmnXml());
        }

        return procesoRepository.save(existente);
    }

    public Optional<ProcesoDefinicion> obtenerPorId(String id) {
        return procesoRepository.findById(id);
    }

    public List<ProcesoDefinicion> obtenerTodos() {
        return procesoRepository.findAll();
    }

    public List<ProcesoDefinicion> obtenerActivos() {
        return procesoRepository.findAll().stream()
                .filter(ProcesoDefinicion::isActivo)
                .collect(Collectors.toList());
    }

    /**
     * Publica una política en borrador.
     * - Valida integridad del flujo
     * - Asigna versión
     * - Marca versión anterior como OBSOLETA
     * - Registra en auditoría
     */
    public ProcesoDefinicion publicar(String procesoId, String usernameAdmin) {
        ProcesoDefinicion borrador = procesoRepository.findById(procesoId)
                .orElseThrow(() -> new RuntimeException("Política no encontrada"));

        if (borrador.getEstado() != EstadoProceso.BORRADOR) {
            throw new RuntimeException(
                    "Solo se pueden publicar políticas en estado BORRADOR. Estado actual: " + borrador.getEstado());
        }

        // 1. Validar integridad
        List<String> erroresValidacion = validarIntegridad(borrador);
        if (!erroresValidacion.isEmpty()) {
            throw new RuntimeException("Errores de integridad: " + String.join(" | ", erroresValidacion));
        }

        // 2. Determinar código base (si no tiene, usar el código normal)
        String codigoBase = borrador.getCodigoBase() != null
                ? borrador.getCodigoBase()
                : borrador.getCodigo();
        borrador.setCodigoBase(codigoBase);

        // 3. Buscar versión activa previa del mismo codigoBase
        Optional<ProcesoDefinicion> activaAnterior = procesoRepository
                .findByCodigoBaseAndEstado(codigoBase, EstadoProceso.ACTIVA);

        int nuevoNumeroVersion = 1;
        if (activaAnterior.isPresent()) {
            ProcesoDefinicion anterior = activaAnterior.get();
            // Marcar anterior como OBSOLETA
            anterior.setEstado(EstadoProceso.OBSOLETA);
            anterior.setMotivoObsolescencia("Reemplazada por versión nueva publicada el "
                    + LocalDateTime.now() + " por " + usernameAdmin);
            procesoRepository.save(anterior);

            nuevoNumeroVersion = (anterior.getNumeroVersion() != null ? anterior.getNumeroVersion() : 1) + 1;
        }

        // 4. Asignar versión y estado al borrador
        borrador.setEstado(EstadoProceso.ACTIVA);
        borrador.setNumeroVersion(nuevoNumeroVersion);
        borrador.setVersion("v" + nuevoNumeroVersion + ".0");
        borrador.setPublicadoPor(usernameAdmin);
        borrador.setFechaPublicacion(LocalDateTime.now());
        borrador.setActivo(true);

        ProcesoDefinicion publicado = procesoRepository.save(borrador);

        // 5. Registrar auditoría
        AuditLog log = new AuditLog();
        log.setTramiteId("SISTEMA_POLITICAS");
        log.setUsuarioId(usernameAdmin);
        log.setDepartamentoId("SISTEMA");
        log.setAccion("POLITICA_PUBLICADA");
        log.setDetalle("Publicada política '" + publicado.getNombre() + "' " + publicado.getVersion()
                + (activaAnterior.isPresent() ? " (reemplaza a " + activaAnterior.get().getVersion() + ")"
                        : " (primera versión)"));
        log.setFechaTimestamp(LocalDateTime.now());
        auditLogRepository.save(log);

        return publicado;
    }

    /**
     * Crea una nueva versión en borrador basada en una política ya publicada.
     * Esto permite editar sin romper trámites activos.
     */
    public ProcesoDefinicion crearNuevaVersion(String procesoOriginalId, String usernameAdmin) {
        ProcesoDefinicion original = procesoRepository.findById(procesoOriginalId)
                .orElseThrow(() -> new RuntimeException("Política original no encontrada"));

        // Clonar campos esenciales
        ProcesoDefinicion nueva = new ProcesoDefinicion();
        nueva.setCodigo(original.getCodigo());
        nueva.setCodigoBase(original.getCodigoBase() != null ? original.getCodigoBase() : original.getCodigo());
        nueva.setNombre(original.getNombre());
        nueva.setDescripcion(original.getDescripcion());
        nueva.setBpmnXml(original.getBpmnXml());
        nueva.setSvgPreview(original.getSvgPreview());
        nueva.setPasos(original.getPasos());
        nueva.setPasoInicialId(original.getPasoInicialId());
        nueva.setEstado(EstadoProceso.BORRADOR);
        nueva.setNumeroVersion(null); // se asigna al publicar
        nueva.setActivo(false); // inactivo hasta publicar
        nueva.setFechaCreacion(LocalDateTime.now());

        return procesoRepository.save(nueva);
    }

    /**
     * Valida la integridad del flujo antes de publicar.
     * Retorna lista de errores (vacía si todo está bien).
     */
    public List<String> validarIntegridad(ProcesoDefinicion proceso) {
        List<String> errores = new ArrayList<>();

        // 1. Debe tener al menos un paso
        if (proceso.getPasos() == null || proceso.getPasos().isEmpty()) {
            errores.add("La política no tiene ningún paso definido");
            return errores;
        }

        // 2. Debe tener pasoInicialId
        if (proceso.getPasoInicialId() == null || proceso.getPasoInicialId().isBlank()) {
            errores.add("La política no tiene un paso inicial definido");
        }

        // 3. Validar cada paso
        for (Paso p : proceso.getPasos()) {
            // 3.1 Cada tarea de usuario debe tener departamento asignado
            if (p.getTipo() == TipoPaso.TAREA) {
                String depto = p.getDepartamentoAsignadoId();
                if (depto == null || depto.isBlank()
                        || depto.equals("SIN_ASIGNAR")
                        || depto.startsWith("NO_EXISTE:")) {
                    errores.add("El paso '" + p.getNombre() + "' no tiene un departamento válido asignado");
                }
            }

            // 3.2 Pasos que NO son de fin deben tener al menos una transición
            if (p.getTipo() == TipoPaso.TAREA
                    || p.getTipo() == TipoPaso.GATEWAY_EXCLUSIVO
                    || p.getTipo() == TipoPaso.GATEWAY_PARALELO_SPLIT) {
                if (p.getTransiciones() == null || p.getTransiciones().isEmpty()) {
                    errores.add("El paso '" + p.getNombre() + "' no tiene transiciones de salida (nodo huérfano)");
                }
            }

            // 3.3 Gateway XOR debe tener al menos 2 salidas
            if (p.getTipo() == TipoPaso.GATEWAY_EXCLUSIVO) {
                if (p.getTransiciones() == null || p.getTransiciones().size() < 2) {
                    errores.add("El gateway '" + p.getNombre() + "' debe tener al menos 2 salidas (actualmente tiene "
                            + (p.getTransiciones() == null ? 0 : p.getTransiciones().size()) + ")");
                }
                // Verificar que todas las transiciones tengan nombre
                if (p.getTransiciones() != null) {
                    for (Transicion t : p.getTransiciones()) {
                        if ((t.getNombreAccion() == null || t.getNombreAccion().isBlank())
                                && (t.getEstadoCondicion() == null || t.getEstadoCondicion().isBlank()
                                        || "DEFAULT".equalsIgnoreCase(t.getEstadoCondicion()))) {
                            errores.add("El gateway '" + p.getNombre()
                                    + "' tiene una flecha sin nombre. Todas las salidas de un gateway de decisión deben tener un nombre (ej: APROBADO, RECHAZADO)");
                        }
                    }
                }
            }
        }

        // 4. Verificar que los IDs de destino de transiciones existen
        Set<String> idsPasos = proceso.getPasos().stream()
                .map(Paso::getId)
                .collect(java.util.stream.Collectors.toSet());

        for (Paso p : proceso.getPasos()) {
            if (p.getTransiciones() != null) {
                for (Transicion t : p.getTransiciones()) {
                    String destino = t.getPasoDestinoId();
                    if (destino != null && !destino.equals("FIN") && !destino.equals("FIN_TERMINA_TODO")
                            && !idsPasos.contains(destino)) {
                        errores.add("El paso '" + p.getNombre()
                                + "' tiene una transición que apunta a un paso inexistente: " + destino);
                    }
                }
            }
        }

        return errores;
    }

    /**
     * Obtiene el historial de versiones de una política.
     */
    public List<ProcesoDefinicion> obtenerHistorialVersiones(String codigoBase) {
        return procesoRepository.findByCodigoBaseOrderByNumeroVersionDesc(codigoBase);
    }

    public List<ProcesoDefinicion> obtenerPorEstado(EstadoProceso estado) {
        return procesoRepository.findByEstado(estado);
    }
}