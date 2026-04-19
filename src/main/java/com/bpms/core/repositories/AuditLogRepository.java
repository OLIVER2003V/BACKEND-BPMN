package com.bpms.core.repositories;

import com.bpms.core.models.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    // Busca todo el historial de un expediente específico y lo devuelve ordenado del más antiguo al más nuevo
    List<AuditLog> findByTramiteIdOrderByFechaTimestampAsc(String tramiteId);
}