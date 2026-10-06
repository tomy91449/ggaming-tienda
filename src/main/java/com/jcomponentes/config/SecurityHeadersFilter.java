package com.jcomponentes.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * FILTRO DE SEGURIDAD: se ejecuta antes de cada respuesta y agrega cabeceras
 * HTTP que le dicen al navegador qué tiene permitido hacer con la página.
 * Es una defensa "en profundidad": aunque algún día se cuele un bug de XSS,
 * el navegador bloquea la mayoría de los ataques.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    // Content-Security-Policy: de dónde se pueden cargar scripts, estilos, fuentes, etc.
    // - Solo scripts propios ('self'): nada de JS inline ni de otros dominios.
    // - Estilos propios + Google Fonts; fuentes solo desde Google Fonts.
    // - img data: permite el patrón de circuitos del fondo (SVG embebido en el CSS).
    // - img blob: la vista previa de fotos antes de subirlas en el admin.
    // - frame-ancestors 'none': nadie puede meter la página en un iframe (clickjacking).
    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' https://fonts.googleapis.com",
            "font-src https://fonts.gstatic.com",
            "img-src 'self' data: blob:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CSP);
        // No adivinar el tipo de archivo (evita que un .txt se ejecute como script).
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Respaldo de frame-ancestors para navegadores viejos.
        response.setHeader("X-Frame-Options", "DENY");
        // Al salir hacia otro sitio (wa.me), no mandar la URL completa con la búsqueda.
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        // La página no usa cámara, micrófono ni ubicación: los bloqueamos.
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        // Con HTTPS (en el hosting), obligar a usar siempre HTTPS.
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        chain.doFilter(request, response);
    }
}
