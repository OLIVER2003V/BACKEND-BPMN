package com.bpms.core.security;

import com.bpms.core.services.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Buscar el gafete (Token) en la cabecera de la petición
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // Si no hay token o no empieza con "Bearer ", lo dejamos pasar (Spring Security lo bloqueará después si la ruta es privada)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extraer el token (quitando la palabra "Bearer ")
        jwt = authHeader.substring(7);
        username = jwtUtil.extractUsername(jwt);

        // 3. Si hay un usuario en el token y aún no ha iniciado sesión en este request...
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            // 4. Si el token es válido, le damos luz verde para entrar
            if (jwtUtil.validateToken(jwt, userDetails.getUsername())) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // Guardamos al usuario en la memoria temporal de Spring
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        
        // Continuar con la petición
        filterChain.doFilter(request, response);
    }
}