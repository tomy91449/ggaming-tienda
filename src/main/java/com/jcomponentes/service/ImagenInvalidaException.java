package com.jcomponentes.service;

/** Se lanza cuando un archivo subido no es una imagen válida. El mensaje se muestra al admin. */
public class ImagenInvalidaException extends RuntimeException {
    public ImagenInvalidaException(String mensaje) {
        super(mensaje);
    }
}
