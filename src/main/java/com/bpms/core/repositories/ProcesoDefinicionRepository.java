package com.bpms.core.repositories;

import com.bpms.core.models.ProcesoDefinicion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcesoDefinicionRepository extends MongoRepository<ProcesoDefinicion, String> {
    // 👇 Le agregamos "First" para que traiga solo uno, aunque haya repetidos 👇
    Optional<ProcesoDefinicion> findFirstByCodigo(String codigo);
}