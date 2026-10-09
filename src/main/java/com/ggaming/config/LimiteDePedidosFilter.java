package com.ggaming.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * LÍMITE DE PEDIDOS POR IP (protección contra bots y ataques de saturación).
 * Corre antes que todo lo demás: si una IP supera el límite, recibe "429 Too Many Requests"
 * sin llegar a tocar la base de datos.
 *
 * No cuenta los archivos estáticos (CSS, JS, imágenes): una sola visita los pide de a muchos.
 * Tampoco el webhook de WhatsApp: ahí los pedidos vienen de Meta y se validan con su firma.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LimiteDePedidosFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LimiteDePedidosFilter.class);

    private final LimitadorPorIp limitador;

    public LimiteDePedidosFilter(@Value("${app.limite-por-minuto:120}") int limitePorMinuto) {
        this.limitador = new LimitadorPorIp(limitePorMinuto);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return ruta.startsWith("/css/") || ruta.startsWith("/js/") || ruta.startsWith("/img/")
                || ruta.startsWith("/fotos/") || ruta.startsWith("/favicon") || ruta.startsWith("/webhook/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // getRemoteAddr ya es la IP real del cliente: Tomcat la toma de X-Forwarded-For
        // solo si el pedido viene del proxy interno (Caddy). Ver server.forward-headers-strategy.
        String ip = request.getRemoteAddr();
        if (!limitador.permitir(ip, System.currentTimeMillis())) {
            log.warn("Límite de pedidos superado por {}", ip);
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Demasiados pedidos seguidos. Esperá un minuto y volvé a intentar.");
            return;
        }
        chain.doFilter(request, response);
    }
}
