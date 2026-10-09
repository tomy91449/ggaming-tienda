package com.ggaming.ia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cliente HTTP de la API de Gemini (Google AI Studio), endpoint generateContent.
 * Solo sabe "hablar HTTP" con Google: los prompts están en GeminiAsistente.
 *
 * Seguridad: la API key viaja en una cabecera (x-goog-api-key), nunca en la URL
 * (las URLs quedan en logs) y nunca llega al navegador.
 */
@Component
public class GeminiCliente {

    private static final Logger log = LoggerFactory.getLogger(GeminiCliente.class);

    private final RestClient http;
    private final IaProperties config;
    private final ObjectMapper json;

    /** @param modelo qué modelo respondió (el principal o el de respaldo) */
    public record Respuesta(String texto, List<SugerenciaIa.Fuente> fuentes, String modelo) {}

    public GeminiCliente(RestClient.Builder builder, IaProperties config, ObjectMapper json) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(60)); // con búsqueda en Google puede tardar
        this.http = builder.baseUrl(config.urlBase()).requestFactory(fabrica).build();
        this.config = config;
        this.json = json;
    }

    /**
     * Prueba con el modelo principal y, si se quedó sin cuota o está saturado,
     * con el de respaldo (ej: gemini-3.5-flash → gemini-3.5-flash-lite).
     *
     * @param contenidos mensajes en formato Gemini: [{role: "user"|"model", parts: [{text}]}]
     * @param json       pedir respuesta en JSON
     * @param buscar     permitir que busque en Google
     */
    public Respuesta generar(String sistema, List<Map<String, Object>> contenidos, boolean json, boolean buscar) {
        List<String> modelos = config.modelosEnOrden();
        IaException ultimo = null;
        for (String modelo : modelos) {
            try {
                return generarCon(modelo, sistema, contenidos, json, buscar);
            } catch (IaException e) {
                if (!e.reintentable()) throw e;
                log.info("Modelo {} no respondió bien ({}), pruebo con el siguiente.", modelo, e.getMessage());
                ultimo = e;
            }
        }
        throw ultimo != null ? ultimo : new IaException("No hay ningún modelo de IA configurado (GEMINI_MODEL).");
    }

    private Respuesta generarCon(String modelo, String sistema, List<Map<String, Object>> contenidos,
                                 boolean json, boolean buscar) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("systemInstruction", Map.of("parts", List.of(Map.of("text", sistema))));
        cuerpo.put("contents", contenidos);
        Map<String, Object> generacion = new LinkedHashMap<>();
        generacion.put("temperature", 0.4);
        // Con búsqueda activada pedimos el JSON en el prompt (más compatible entre modelos).
        if (json && !buscar) generacion.put("responseMimeType", "application/json");
        cuerpo.put("generationConfig", generacion);
        if (buscar) cuerpo.put("tools", List.of(Map.of("google_search", Map.of())));

        String texto;
        try {
            // Leemos como texto y parseamos nosotros: cuando Google está saturado a veces declara
            // un tipo de contenido raro (application/octet-stream) aunque mande JSON.
            texto = http.post()
                    .uri("/models/{modelo}:generateContent", modelo)
                    .header("x-goog-api-key", config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (pedido, respuesta) -> {
                        String detalle = new String(respuesta.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("Gemini respondió {}: {}", respuesta.getStatusCode().value(),
                                detalle.length() > 500 ? detalle.substring(0, 500) : detalle);
                        int estado = respuesta.getStatusCode().value();
                        // 429 = sin cuota, 503/500 = saturado: el modelo de respaldo puede responder.
                        boolean reintentable = estado == 429 || estado >= 500;
                        throw new IaException(mensajePorEstado(estado, detalle, modelo), reintentable);
                    })
                    .body(String.class);
        } catch (IaException e) {
            throw e;
        } catch (ResourceAccessException e) {
            // Timeout o corte de conexión: puede ser momentáneo, el modelo de respaldo lo puede resolver.
            throw new IaException("No se pudo conectar con Gemini (¿sin internet o tardó demasiado?).", true);
        } catch (RestClientException e) {
            log.warn("Respuesta inesperada de Gemini: {}", e.getMessage());
            throw new IaException("Gemini devolvió una respuesta inesperada.", true);
        }
        JsonNode raiz;
        try {
            // this.json (el parser) y no "json" (el parámetro que dice si pedir JSON)
            raiz = texto == null || texto.isBlank() ? null : this.json.readTree(texto);
        } catch (JsonProcessingException e) {
            throw new IaException("Gemini devolvió una respuesta inesperada.", true);
        }
        return leer(raiz, modelo);
    }

    private Respuesta leer(JsonNode raiz, String modelo) {
        JsonNode candidato = raiz == null ? null : raiz.path("candidates").path(0);
        if (candidato == null || candidato.isMissingNode()) {
            String motivo = raiz == null ? "" : raiz.path("promptFeedback").path("blockReason").asText("");
            throw new IaException(motivo.isEmpty() ? "Gemini no devolvió respuesta."
                    : "Gemini bloqueó la consulta (" + motivo + ").");
        }
        StringBuilder texto = new StringBuilder();
        for (JsonNode parte : candidato.path("content").path("parts")) {
            if (parte.path("thought").asBoolean(false)) continue; // "pensamientos" internos del modelo
            texto.append(parte.path("text").asText(""));
        }
        if (texto.isEmpty()) {
            throw new IaException("Gemini respondió vacío (" + candidato.path("finishReason").asText("?") + ").");
        }
        // Fuentes de la búsqueda en Google (si buscó)
        Set<String> vistas = new LinkedHashSet<>();
        List<SugerenciaIa.Fuente> fuentes = new ArrayList<>();
        for (JsonNode chunk : candidato.path("groundingMetadata").path("groundingChunks")) {
            String url = chunk.path("web").path("uri").asText("");
            if (url.startsWith("https://") && vistas.add(url) && fuentes.size() < 5) {
                fuentes.add(new SugerenciaIa.Fuente(chunk.path("web").path("title").asText(url), url));
            }
        }
        return new Respuesta(texto.toString(), fuentes, modelo);
    }

    private String mensajePorEstado(int estado, String detalle, String modelo) {
        if (detalle.contains("API key") || estado == 401 || estado == 403) {
            return "La API key de Gemini no es válida o no tiene permiso. Revisá GEMINI_API_KEY en el .env.";
        }
        return switch (estado) {
            case 404 -> "El modelo '" + modelo + "' no existe. Revisá GEMINI_MODEL en el .env.";
            case 429 -> "Se agotó el plan gratuito de Gemini por ahora (también el modelo de respaldo). "
                    + "Probá en unos minutos; mientras tanto la categoría y la descripción se completan sin IA.";
            case 400 -> "Gemini rechazó el pedido (" + estado + "). Revisá GEMINI_MODEL.";
            default -> estado >= 500 ? "Gemini está con problemas en este momento. Probá en un rato."
                    : "Error de Gemini (" + estado + ").";
        };
    }
}
