package com.bpms.core.repositories;

import com.bpms.core.models.Departamento;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartamentoRepository extends MongoRepository<Departamento, String> {
    // Aquí podemos agregar búsquedas personalizadas luego, 
    // pero por ahora el estándar de MongoRepository es suficiente.
}