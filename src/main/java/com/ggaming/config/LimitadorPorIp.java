package com.ggaming.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cuenta pedidos por IP en ventanas de 1 minuto.
 * Una persona navegando hace 10-30 pedidos por minuto; un bot o un ataque, cientos o miles.
 * Con un límite generoso (120/min) nadie normal lo nota y los abusos se frenan.
 */
public class LimitadorPorIp {

    private static final int MAX_IPS = 20_000; // tope de memoria

    private record Ventana(long minuto, int pedidos) {}

    private final int limitePorMinuto;
    private final Map<String, Ventana> porIp = new ConcurrentHashMap<>();

    public LimitadorPorIp(int limitePorMinuto) {
        this.limitePorMinuto = limitePorMinuto;
    }

    /** @return true si el pedido puede pasar; false si esa IP superó el límite en este minuto. */
    public boolean permitir(String ip, long ahoraMillis) {
        long minuto = ahoraMillis / 60_000;
        if (porIp.size() > MAX_IPS) porIp.clear();
        Ventana v = porIp.merge(ip, new Ventana(minuto, 1), (vieja, nueva) ->
                vieja.minuto() == minuto ? new Ventana(minuto, vieja.pedidos() + 1) : nueva);
        return v.pedidos() <= limitePorMinuto;
    }
}
