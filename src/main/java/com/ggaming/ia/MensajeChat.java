package com.ggaming.ia;

/**
 * Un mensaje de la conversación con el asistente.
 *
 * @param rol "usuario" o "asistente"
 */
public record MensajeChat(String rol, String texto) {
}
