package com.jcomponentes.config;

import com.jcomponentes.service.ImagenService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Publica la carpeta de fotos subidas en la URL /fotos/...
 * Las fotos tienen nombre aleatorio y nunca cambian, así que el navegador
 * puede guardarlas en caché por mucho tiempo (la tienda carga más rápido).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ImagenService imagenes;

    public WebConfig(ImagenService imagenes) {
        this.imagenes = imagenes;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ImagenService.PREFIJO + "**")
                .addResourceLocations(imagenes.carpeta().toUri().toString())
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
    }
}
