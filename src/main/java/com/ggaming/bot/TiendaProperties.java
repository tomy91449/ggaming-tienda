package com.ggaming.bot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Datos de la tienda que usa el bot para responder (sección app.tienda.*).
 * Los textos por defecto están acá (y no en application.properties) porque
 * ese archivo no lee bien los acentos. Se pueden cambiar desde el .env.
 */
@ConfigurationProperties(prefix = "app.tienda")
public record TiendaProperties(
        String direccion,
        String apertura,
        String cierre,
        String pagos,
        String entrega,
        String garantia,
        String zonaHoraria,
        String mapsApiKey
) {
    public TiendaProperties {
        direccion = vacio(direccion) ? "Blvr. Chacabuco 651, X5000IIG Córdoba" : direccion;
        apertura = vacio(apertura) ? "08:00" : apertura;
        cierre = vacio(cierre) ? "21:00" : cierre;
        pagos = vacio(pagos) ? "Transferencia o efectivo" : pagos;
        entrega = vacio(entrega)
                ? "Retiro en persona en el local, o envío por Uber Envíos con el costo a cargo del comprador"
                : entrega;
        garantia = vacio(garantia)
                ? "No damos garantía porque los productos son usados, pero podés probar el producto "
                  + "en el local antes de comprarlo para verificar que ande todo bien."
                : garantia;
        zonaHoraria = vacio(zonaHoraria) ? "America/Argentina/Cordoba" : zonaHoraria;
    }

    public LocalTime horaApertura() {
        return LocalTime.parse(apertura);
    }

    public LocalTime horaCierre() {
        return LocalTime.parse(cierre);
    }

    public ZoneId zona() {
        return ZoneId.of(zonaHoraria);
    }

    // ---------------------------------------------------------------- Google Maps
    // Links oficiales de Google Maps ("Maps URLs"): no necesitan clave ni tienen costo.

    /** Abre la ubicación en Google Maps (app en el celular, web en la PC). */
    public String urlMapa() {
        return "https://www.google.com/maps/search/?api=1&query=" + codificar(direccion);
    }

    /** Abre Google Maps con la ruta desde donde esté el cliente hasta la tienda. */
    public String urlComoLlegar() {
        return "https://www.google.com/maps/dir/?api=1&destination=" + codificar(direccion);
    }

    /**
     * Mapa para incrustar en la página (iframe).
     * Con GOOGLE_MAPS_API_KEY usa la Maps Embed API oficial (gratis, sin límite de uso);
     * sin clave, usa la versión pública de Google Maps para incrustar.
     */
    public String urlMapaIncrustado() {
        if (!vacio(mapsApiKey)) {
            return "https://www.google.com/maps/embed/v1/place?key=" + codificar(mapsApiKey)
                    + "&q=" + codificar(direccion) + "&zoom=16&language=es";
        }
        return "https://maps.google.com/maps?q=" + codificar(direccion) + "&z=16&hl=es&output=embed";
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
