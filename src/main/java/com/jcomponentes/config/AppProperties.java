package com.jcomponentes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la app, leída de application.properties, que a su vez
 * toma los valores de las variables de entorno (WHATSAPP_NUMBER, BASE_URL,
 * ADMIN_USER, ADMIN_PASSWORD, SECRET_KEY, UPLOADS_DIR).
 *
 * Al ser un record, es inmutable: se carga una vez al arrancar y listo.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String whatsappNumber,
        String baseUrl,
        String adminUser,
        String adminPassword,
        String secretKey,
        String uploadsDir
) {
}
