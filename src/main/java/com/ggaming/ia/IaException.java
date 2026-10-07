package com.ggaming.ia;

/** Error de la IA con un mensaje entendible para mostrarle al admin. */
public class IaException extends RuntimeException {
    public IaException(String mensaje) {
        super(mensaje);
    }
}
