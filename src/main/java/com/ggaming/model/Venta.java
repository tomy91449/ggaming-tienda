package com.ggaming.model;

import java.time.LocalDate;

/**
 * Una venta registrada: a cuánto se publicó y a cuánto se vendió realmente.
 * Es la "memoria de precios" del negocio.
 *
 * @param productoId puede ser null o apuntar a un producto ya borrado
 */
public record Venta(
        Long id,
        Long productoId,
        String nombre,
        Categoria categoria,
        long precioPublicado,
        long precioVenta,
        LocalDate fecha
) {
}
