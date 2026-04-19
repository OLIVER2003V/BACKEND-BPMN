package com.bpms.core.services;

import com.bpms.core.models.Usuario;
import com.bpms.core.repositories.UsuarioRepository;
import com.bpms.core.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    // Registro de nuevos usuarios
    public Usuario registrar(Usuario usuario) {
        // AQUÍ OCURRE LA MAGIA CORRECTA (Solo 1 vez)
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));

        // 👇 NUEVO: setear valores por defecto al registrar
        usuario.setFechaCreacion(LocalDateTime.now());
        if (usuario.getEstadoDisponibilidad() == null) {
            usuario.setEstadoDisponibilidad("DISPONIBLE");
        }

        return usuarioRepository.save(usuario);
    }

    // Proceso de Login
    public Map<String, String> login(String username, String password) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // AQUÍ SE COMPARA CORRECTAMENTE
        if (passwordEncoder.matches(password, usuario.getPassword())) {

            // 👇 NUEVO: actualizar última conexión cada vez que hace login
            usuario.setUltimaConexion(LocalDateTime.now());
            usuarioRepository.save(usuario);

            String token = jwtUtil.generateToken(username);
            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            response.put("username", usuario.getUsername());
            response.put("rol", usuario.getRol().name());

            if (usuario.getDepartamentoId() != null) {
                response.put("departamentoId", usuario.getDepartamentoId());
            }
            return response;
        } else {
            throw new RuntimeException("Contraseña incorrecta");
        }
    }
}