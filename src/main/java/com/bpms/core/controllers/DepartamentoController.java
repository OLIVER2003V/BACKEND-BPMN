package com.bpms.core.controllers;

import com.bpms.core.models.Departamento;
import com.bpms.core.repositories.DepartamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departamentos")
public class DepartamentoController {

    @Autowired
    private DepartamentoRepository departamentoRepository;

    // 1. Obtener todos los departamentos (útil para llenar listas desplegables)
    @GetMapping
    public List<Departamento> obtenerTodos() {
        return departamentoRepository.findAll();
    }

    // 2. Crear un nuevo departamento
    @PostMapping
    public Departamento crear(@RequestBody Departamento departamento) {
        return departamentoRepository.save(departamento);
    }

    // 3. Obtener un departamento por su ID
    @GetMapping("/{id}")
    public Departamento obtenerPorId(@PathVariable String id) {
        return departamentoRepository.findById(id).orElse(null);
    }
}