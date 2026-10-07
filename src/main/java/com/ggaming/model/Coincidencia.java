package com.ggaming.model;

import java.time.LocalDate;

/**
 * Un producto de tu historial que se parece al que estás cargando.
 *
 * @param origen       "VENDIDO" (sale del historial de ventas) o "PUBLICADO" (está en el catálogo ahora)
 * @param precioVenta  null si todavía no se vendió
 * @param fecha        fecha de venta (null si está publicado)
 * @param similitud    0.0 a 1.0
 * @param mismoModelo  true si es el mismo modelo exacto (ej: "RTX 3060 12GB" vs "MSI RTX 3060 12GB")
 */
public record Coincidencia(
        String origen,
        String nombre,
        long precioPublicado,
        Long precioVenta,
        LocalDate fecha,
        double similitud,
        boolean mismoModelo
) {
}
