package com.jcomponentes.ia;

/**
 * Un mensaje de la conversación con el asistente.
 *
 * @param rol "usuario" o "asistente"
 */
public record MensajeChat(String rol, String texto) {
}
