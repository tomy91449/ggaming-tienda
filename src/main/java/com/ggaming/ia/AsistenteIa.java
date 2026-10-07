package com.ggaming.ia;

import com.ggaming.model.Coincidencia;

import java.util.List;

/**
 * INTERFAZ de la IA (patrón Strategy).
 *
 * El resto de la app solo conoce esta interfaz, no a Gemini. Para cambiar de
 * proveedor (por ejemplo, la API de Claude), se crea otra clase que la implemente
 * y se marca con @Primary: no hay que tocar controladores, vistas ni servicios.
 */
public interface AsistenteIa {

    /** false si falta la API key: la app sigue funcionando, sin IA. */
    boolean disponible();

    /** Nombre para mostrar, ej: "Gemini (gemini-3.5-flash)". */
    String nombre();

    /**
     * A partir del nombre de un producto y de los parecidos de tu historial,
     * propone categoría, descripción, ficha técnica y precio.
     */
    SugerenciaIa sugerirProducto(String nombreProducto, List<Coincidencia> historial);

    /**
     * Responde una pregunta del admin.
     *
     * @param conversacion mensajes anteriores (para que entienda "¿y la otra?")
     * @param datosTienda  resumen de productos y ventas, como texto
     */
    String responder(String pregunta, List<MensajeChat> conversacion, String datosTienda);
}
