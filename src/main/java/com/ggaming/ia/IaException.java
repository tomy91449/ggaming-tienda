package com.ggaming.ia;

/**
 * Error de la IA con un mensaje entendible para mostrarle al admin.
 *
 * @see #reintentable() si vale la pena probar con el modelo de respaldo
 */
public class IaException extends RuntimeException {

    private final boolean reintentable;

    public IaException(String mensaje) {
        this(mensaje, false);
    }

    public IaException(String mensaje, boolean reintentable) {
        super(mensaje);
        this.reintentable = reintentable;
    }

    /** true si el modelo se quedó sin cuota o está saturado (otro modelo puede responder). */
    public boolean reintentable() {
        return reintentable;
    }
}
