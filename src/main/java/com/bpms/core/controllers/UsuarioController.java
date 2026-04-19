package com.bpms.core.controllers;

import com.bpms.core.models.Usuario;
import com.bpms.core.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public List<Usuario> obtenerTodos() {
        return usuarioRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable String id) {
        return usuarioRepository.findById(id)
                .map(u -> ResponseEntity.ok((Object) u))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crearUsuario(@RequestBody Usuario usuario) {
        // Validar que el username no exista
        if (usuarioRepository.findByUsername(usuario.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("El nombre de usuario ya está en uso");
        }

        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuario.setFechaCreacion(LocalDateTime.now());
        if (usuario.getEstadoDisponibilidad() == null) {
            usuario.setEstadoDisponibilidad("DISPONIBLE");
        }

        return ResponseEntity.ok(usuarioRepository.save(usuario));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarUsuario(@PathVariable String id, @RequestBody Usuario datos) {
        return usuarioRepository.findById(id)
                .map(existente -> {
                    // Campos de identidad
                    if (datos.getUsername() != null)
                        existente.setUsername(datos.getUsername());
                    if (datos.getNombreCompleto() != null)
                        existente.setNombreCompleto(datos.getNombreCompleto());
                    if (datos.getEmail() != null)
                        existente.setEmail(datos.getEmail());

                    // Campos operativos
                    if (datos.getRol() != null)
                        existente.setRol(datos.getRol());
                    if (datos.getDepartamentoId() != null)
                        existente.setDepartamentoId(datos.getDepartamentoId());
                    if (datos.getEstadoDisponibilidad() != null)
                        existente.setEstadoDisponibilidad(datos.getEstadoDisponibilidad());

                    // Password opcional (solo si viene con contenido)
                    if (datos.getPassword() != null && !datos.getPassword().isBlank()) {
                        existente.setPassword(passwordEncoder.encode(datos.getPassword()));
                    }

                    return ResponseEntity.ok((Object) usuarioRepository.save(existente));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<?> actualizarEstado(@PathVariable String id, @RequestBody Map<String, String> body) {
        String nuevoEstado = body.get("estado");
        if (nuevoEstado == null || !List.of("DISPONIBLE", "AUSENTE", "VACACIONES").contains(nuevoEstado)) {
            return ResponseEntity.badRequest().body("Estado no válido. Debe ser: DISPONIBLE, AUSENTE o VACACIONES");
        }

        return usuarioRepository.findById(id)
                .map(usuario -> {
                    usuario.setEstadoDisponibilidad(nuevoEstado);
                    return ResponseEntity.ok((Object) usuarioRepository.save(usuario));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarUsuario(@PathVariable String id) {
        if (!usuarioRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        usuarioRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("mensaje", "Usuario eliminado correctamente"));
    }
}