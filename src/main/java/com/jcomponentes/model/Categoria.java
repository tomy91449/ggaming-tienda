package com.jcomponentes.model;

/**
 * Categorías fijas del catálogo. Usar un enum (y no texto libre) evita
 * errores de tipeo como "Placa de video" vs "placas de video".
 * En la base se guarda el nombre de la constante (ej: "PLACA_VIDEO").
 * El ícono de cada categoría es un SVG en templates/fragments/iconos.html.
 */
public enum Categoria {
    PLACA_VIDEO("Placas de video"),
    PROCESADOR("Procesadores"),
    RAM("Memorias RAM"),
    DISCO("Discos"),
    FUENTE("Fuentes"),
    MOTHERBOARD("Motherboards"),
    GABINETE("Gabinetes"),
    OTRO("Otros");

    private final String etiqueta; // texto que ve el usuario

    Categoria(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() { return etiqueta; }
}
