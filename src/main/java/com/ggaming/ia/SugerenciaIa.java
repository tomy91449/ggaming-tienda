package com.ggaming.ia;

import java.util.List;

/**
 * Lo que la IA propone para un producto a partir de su nombre.
 * Todo es editable: son sugerencias, el admin decide qué usar.
 *
 * @param categoria  nombre del enum Categoria (ej: "PLACA_VIDEO")
 * @param fuentes    páginas que consultó en Google para el precio (puede estar vacía)
 * @param modelo     qué modelo de IA respondió (ej: "gemini-3.5-flash-lite")
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
        List<Fuente> fuentes,
        String modeloIa
) {
    public record Fuente(String titulo, String url) {}

    public SugerenciaIa conOrigen(List<Fuente> nuevasFuentes, String modeloQueRespondio) {
        return new SugerenciaIa(categoria, marca, modelo, descripcion, specs,
                precioRecomendado, precioMin, precioMax, explicacion, nuevasFuentes, modeloQueRespondio);
    }
}
