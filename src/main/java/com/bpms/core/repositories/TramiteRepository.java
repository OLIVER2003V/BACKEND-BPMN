package com.bpms.core.repositories;

import com.bpms.core.models.Tramite;
import com.bpms.core.models.TipoResponsable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TramiteRepository extends MongoRepository<Tramite, String> {

    // === Búsquedas por departamento (funcionarios) ===
    List<Tramite> findByDepartamentoActualId(String departamentoId);

    // === Búsquedas por cliente ===
    List<Tramite> findByClienteIdOrderByFechaCreacionDesc(String clienteId);


    List<Tramite> findByClienteIdAndTipoResponsableActual(String clienteId, TipoResponsable tipo);

    // === Búsquedas para Minería de Procesos ===
    List<Tramite> findByProcesoDefinicionId(String procesoDefinicionId);

    // === Código de seguimiento (rastreo público) ===
    Optional<Tramite> findByCodigoSeguimiento(String codigo);

    // === Conteo por estado (para dashboard) ===
    @Query(value = "{ 'estadoSemaforo': ?0 }", count = true)
    long contarPorEstado(String estado);
}