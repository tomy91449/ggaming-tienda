package com.jcomponentes.ia;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la IA (sección app.ia.* de application.properties,
 * que sale de GEMINI_API_KEY, GEMINI_MODEL, GEMINI_BUSQUEDA en el .env).
 *
 * @param busquedaWeb si true, Gemini puede buscar en Google (ej: precios actuales)
 */
@ConfigurationProperties(prefix = "app.ia")
public record IaProperties(
        String apiKey,
        String modelo,
        boolean busquedaWeb,
        String urlBase
) {
    public boolean configurada() {
        return apiKey != null && !apiKey.isBlank();
    }
}
