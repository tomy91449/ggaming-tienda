package com.ggaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada. Spring Boot arranca un servidor web (Tomcat) embebido,
 * escanea este paquete y "conecta" automáticamente controladores, servicios
 * y repositorios entre sí (inyección de dependencias).
 */
@SpringBootApplication
@ConfigurationPropertiesScan // habilita las clases @ConfigurationProperties (ver AppProperties)
public class GGamingApplication {

    public static void main(String[] args) {
        SpringApplication.run(GGamingApplication.class, args);
    }
}
