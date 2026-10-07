package com.ggaming.model;

/**
 * MODELO: representa un producto tal como existe en el negocio.
 * No sabe nada de HTML ni de SQL; solo datos.
 *
 * @param precio precio referencial en pesos, sin centavos
 * @param foto   ruta de la foto principal (null si todavía no tiene)
 */
public record Producto(
        Long id,
        String nombre,
        String descripcion,
        Categoria categoria,
        long precio,
        boolean vendido,
        String foto
) {
    public boolean disponible() {
        return !vendido;
    }
}
