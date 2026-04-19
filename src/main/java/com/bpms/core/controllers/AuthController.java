package com.bpms.core.controllers;

import com.bpms.core.models.Rol;
import com.bpms.core.models.Usuario;
import com.bpms.core.services.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public Usuario registrar(@RequestBody Usuario usuario) {
        usuario.setRol(Rol.CLIENTE);
        usuario.setDepartamentoId(null);
        // Le quitamos el passwordEncoder de aquí, el AuthService se encarga ahora.
        return authService.registrar(usuario);
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> credentials) {
        return authService.login(credentials.get("username"), credentials.get("password"));
    }
}