package com.jcomponentes.model;

/**
 * Una foto de un producto. Un producto puede tener varias.
 *
 * @param ruta ruta pública, ej: "/fotos/3f2a9c.jpg"
 */
public record Foto(Long id, long productoId, String ruta, int orden) {
}
