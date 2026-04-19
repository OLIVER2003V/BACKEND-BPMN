package com.bpms.core.repositories;

import com.bpms.core.models.Tramite;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface TramiteRepository extends MongoRepository<Tramite, String> {
    
    // Magia de Spring Boot: Solo con escribir este nombre, 
    // Java sabe que debe buscar trámites que estén en un departamento específico.
    List<Tramite> findByDepartamentoActualId(String departamentoId);
    
    // Para que el cliente vea solo sus propios trámites
    List<Tramite> findByClienteId(String clienteId);

    Optional<Tramite> findByCodigoSeguimiento(String codigoSeguimiento);
    // 👇 Agrega esta línea para poder contar cuántos trámites hay por estado
    @org.springframework.data.mongodb.repository.Query(value = "{ 'estadoSemaforo': ?0 }", count = true)
    long contarPorEstado(String estado);
}