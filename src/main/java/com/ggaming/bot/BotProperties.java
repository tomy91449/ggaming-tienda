package com.ggaming.bot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del bot de WhatsApp (sección app.bot.* de application.properties).
 * Los valores secretos (token, app secret) salen del .env o del panel del hosting.
 *
 * @param token             token de acceso de la WhatsApp Cloud API (Meta)
 * @param phoneNumberId     id del número del bot en Meta (no es el número, es un id numérico)
 * @param verifyToken       palabra secreta que inventás vos para que Meta verifique el webhook
 * @param appSecret         "App Secret" de la app de Meta: valida que los mensajes vienen de Meta
 * @param numeroDueno       tu WhatsApp personal, donde llegan los avisos
 * @param plantillaConsulta plantilla aprobada para avisar consultas (vacío = mensaje de texto común)
 * @param plantillaTurno    plantilla aprobada para avisar turnos (vacío = mensaje de texto común)
 * @param horasPausa        horas que el bot calla en un chat después de derivarlo
 */
@ConfigurationProperties(prefix = "app.bot")
public record BotProperties(
        String token,
        String phoneNumberId,
        String verifyToken,
        String appSecret,
        String numeroDueno,
        String graphUrl,
        String plantillaConsulta,
        String plantillaTurno,
        String idiomaPlantilla,
        int horasPausa,
        int maxMensajesPorHora
) {
    /** true si puede mandar mensajes de verdad por WhatsApp. */
    public boolean configurado() {
        return lleno(token) && lleno(phoneNumberId);
    }

    public static boolean lleno(String s) {
        return s != null && !s.isBlank();
    }
}
