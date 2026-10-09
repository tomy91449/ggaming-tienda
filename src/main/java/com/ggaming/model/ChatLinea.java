package com.ggaming.model;

import java.time.Instant;

/**
 * Un mensaje de una conversación del bot de WhatsApp.
 *
 * @param rol CLIENTE, BOT o AVISO (lo que se le avisó al dueño)
 */
public record ChatLinea(String telefono, String rol, String texto, Instant fecha) {

    public static final String CLIENTE = "CLIENTE";
    public static final String BOT = "BOT";
    public static final String AVISO = "AVISO";
}
