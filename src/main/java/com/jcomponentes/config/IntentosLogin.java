package com.jcomponentes.config;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protección contra fuerza bruta: después de 5 contraseñas incorrectas
 * desde la misma IP, esa IP queda bloqueada 15 minutos.
 * Se guarda en memoria (se reinicia si se reinicia la app; para un solo admin alcanza).
 */
@Component
public class IntentosLogin {

    static final int MAX_FALLOS = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(15);
    private static final int MAX_IPS = 10_000; // tope de memoria

    private record Registro(int fallos, Instant ultimo) {}

    private final Map<String, Registro> porIp = new ConcurrentHashMap<>();

    public boolean bloqueada(String ip) {
        Registro r = porIp.get(ip);
        if (r == null) return false;
        if (r.ultimo().plus(BLOQUEO).isBefore(Instant.now())) {
            porIp.remove(ip); // ya pasó el tiempo: se perdona
            return false;
        }
        return r.fallos() >= MAX_FALLOS;
    }

    public void fallo(String ip) {
        if (porIp.size() > MAX_IPS) porIp.clear();
        porIp.merge(ip, new Registro(1, Instant.now()),
                (viejo, nuevo) -> new Registro(viejo.fallos() + 1, Instant.now()));
    }

    public void exito(String ip) {
        porIp.remove(ip);
    }
}
