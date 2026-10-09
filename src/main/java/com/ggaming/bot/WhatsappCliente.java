package com.ggaming.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente de la WhatsApp Cloud API de Meta: envía mensajes desde el número del bot.
 * Endpoint: POST {graphUrl}/{phoneNumberId}/messages con el token en la cabecera.
 */
@Component
public class WhatsappCliente implements Mensajero {

    private static final Logger log = LoggerFactory.getLogger(WhatsappCliente.class);

    private final RestClient http;
    private final BotProperties config;

    public WhatsappCliente(RestClient.Builder builder, BotProperties config) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(20));
        this.http = builder.baseUrl(config.graphUrl()).requestFactory(fabrica).build();
        this.config = config;
    }

    @Override
    public void enviarTexto(String para, String texto) {
        enviar(Map.of(
                "messaging_product", "whatsapp",
                "to", para,
                "type", "text",
                "text", Map.of("body", texto, "preview_url", false)));
    }

    @Override
    public void enviarPlantilla(String para, String plantilla, List<String> parametros) {
        List<Map<String, Object>> params = parametros.stream()
                .map(p -> Map.<String, Object>of("type", "text", "text", p))
                .toList();
        enviar(Map.of(
                "messaging_product", "whatsapp",
                "to", para,
                "type", "template",
                "template", Map.of(
                        "name", plantilla,
                        "language", Map.of("code", config.idiomaPlantilla()),
                        "components", List.of(Map.of("type", "body", "parameters", params)))));
    }

    private void enviar(Map<String, Object> cuerpo) {
        if (!config.configurado()) {
            log.warn("Bot sin WHATSAPP_TOKEN / WHATSAPP_PHONE_NUMBER_ID: no se envía el mensaje.");
            return;
        }
        try {
            http.post()
                    .uri("/{id}/messages", config.phoneNumberId())
                    .header("Authorization", "Bearer " + config.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (pedido, respuesta) -> {
                        String detalle = new String(respuesta.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        // 131047 = pasaron más de 24 hs desde el último mensaje de esa persona: hace falta plantilla.
                        log.warn("WhatsApp respondió {}: {}", respuesta.getStatusCode().value(),
                                detalle.length() > 400 ? detalle.substring(0, 400) : detalle);
                        throw new MensajeNoEnviado(detalle.contains("131047")
                                ? "Pasaron más de 24 hs desde que esa persona escribió: usá una plantilla."
                                : "WhatsApp rechazó el mensaje (" + respuesta.getStatusCode().value() + ").");
                    })
                    .toBodilessEntity();
        } catch (MensajeNoEnviado e) {
            throw e;
        } catch (RestClientException e) {
            throw new MensajeNoEnviado("No se pudo conectar con WhatsApp.");
        }
    }

    /** No se pudo enviar el mensaje (el motivo es legible). */
    public static class MensajeNoEnviado extends RuntimeException {
        public MensajeNoEnviado(String motivo) {
            super(motivo);
        }
    }
}
