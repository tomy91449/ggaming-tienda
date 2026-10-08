package com.ggaming.ia;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.stream.Stream;

/**
 * Configuración de la IA (sección app.ia.* de application.properties,
 * que sale de GEMINI_API_KEY, GEMINI_MODEL, GEMINI_MODELO_RESPALDO y GEMINI_BUSQUEDA en el .env).
 *
 * @param modeloRespaldo se usa solo si el modelo principal se queda sin cuota
 * @param busquedaWeb    si true, Gemini puede buscar en Google (en el plan gratuito agota la cuota)
 */
@ConfigurationProperties(prefix = "app.ia")
public record IaProperties(
        String apiKey,
        String modelo,
        String modeloRespaldo,
        boolean busquedaWeb,
        String urlBase
) {
    public boolean configurada() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Modelos a probar en orden: el principal y, si se agota, el de respaldo. */
    public List<String> modelosEnOrden() {
        return Stream.of(modelo, modeloRespaldo)
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .toList();
    }
}
