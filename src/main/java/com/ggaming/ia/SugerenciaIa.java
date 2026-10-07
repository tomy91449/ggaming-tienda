package com.ggaming.ia;

import java.util.List;

/**
 * Lo que la IA propone para un producto a partir de su nombre.
 * Todo es editable: son sugerencias, el admin decide qué usar.
 *
 * @param categoria   nombre del enum Categoria (ej: "PLACA_VIDEO")
 * @param fuentes     páginas que consultó en Google para el precio (puede estar vacía)
 */
public record SugerenciaIa(
        String categoria,
        String marca,
        String modelo,
        String descripcion,
        List<String> specs,
        Long precioRecomendado,
        Long precioMin,
        Long precioMax,
        String explicacion,
        List<Fuente> fuentes
) {
    public record Fuente(String titulo, String url) {}

    public SugerenciaIa conFuentes(List<Fuente> nuevas) {
        return new SugerenciaIa(categoria, marca, modelo, descripcion, specs,
                precioRecomendado, precioMin, precioMax, explicacion, nuevas);
    }
}
