package com.ggaming.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggaming.bot.BotProperties;
import com.ggaming.bot.BotService;
import com.ggaming.bot.FirmaWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * WEBHOOK de WhatsApp: la URL a la que Meta le avisa cada vez que un cliente escribe.
 *
 *  GET  /webhook/whatsapp  → Meta verifica que la URL es nuestra (una sola vez, al configurarlo).
 *  POST /webhook/whatsapp  → llega un mensaje. Respondemos "OK" al instante y lo procesamos
 *                            en segundo plano (si tardamos, Meta lo reenvía y responderíamos dos veces).
 */
@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsappWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController.class);
    private static final int MAX_LARGO_MENSAJE = 2000;

    private final BotService bot;
    private final BotProperties config;
    private final ObjectMapper json;
    private final TaskExecutor segundoPlano;

    /** Ids de mensajes ya procesados (Meta a veces reenvía el mismo mensaje). */
    private final Set<String> procesados = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) {
                    return size() > 2000;
                }
            }));

    public WhatsappWebhookController(BotService bot, BotProperties config, ObjectMapper json, TaskExecutor segundoPlano) {
        this.bot = bot;
        this.config = config;
        this.json = json;
        this.segundoPlano = segundoPlano;
    }

    /** Verificación inicial: Meta manda tu verify token y un número (challenge) que hay que devolver. */
    @GetMapping
    public ResponseEntity<String> verificar(@RequestParam(name = "hub.mode", required = false) String modo,
                                            @RequestParam(name = "hub.verify_token", required = false) String token,
                                            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        boolean ok = "subscribe".equals(modo) && config.verifyToken() != null && !config.verifyToken().isBlank()
                && config.verifyToken().equals(token) && challenge != null && challenge.matches("[A-Za-z0-9_-]{1,100}");
        return ok ? ResponseEntity.ok(challenge) : ResponseEntity.status(403).body("verificación inválida");
    }

    @PostMapping
    public ResponseEntity<String> recibir(@RequestHeader(name = "X-Hub-Signature-256", required = false) String firma,
                                          @RequestBody byte[] cuerpo) {
        if (!FirmaWebhook.valida(config.appSecret(), cuerpo, firma)) {
            log.warn("Webhook de WhatsApp con firma inválida: rechazado.");
            return ResponseEntity.status(403).body("firma inválida");
        }
        JsonNode raiz;
        try {
            raiz = json.readTree(cuerpo);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("json inválido");
        }
        for (JsonNode entrada : raiz.path("entry")) {
            for (JsonNode cambio : entrada.path("changes")) {
                JsonNode valor = cambio.path("value");
                String numeroDestino = valor.path("metadata").path("phone_number_id").asText("");
                if (BotProperties.lleno(config.phoneNumberId()) && !config.phoneNumberId().equals(numeroDestino)) {
                    continue; // mensaje para otro número de la misma cuenta
                }
                for (JsonNode mensaje : valor.path("messages")) {
                    procesar(mensaje);
                }
                // "statuses" (entregado, leído) no nos interesan: se ignoran.
            }
        }
        return ResponseEntity.ok("ok");
    }

    private void procesar(JsonNode mensaje) {
        String id = mensaje.path("id").asText("");
        String telefono = mensaje.path("from").asText("").replaceAll("\\D", "");
        if (id.isEmpty() || telefono.isEmpty() || !procesados.add(id)) return; // vacío o repetido

        if ("text".equals(mensaje.path("type").asText())) {
            String texto = mensaje.path("text").path("body").asText("").strip();
            if (texto.isEmpty()) return;
            String recortado = texto.length() > MAX_LARGO_MENSAJE ? texto.substring(0, MAX_LARGO_MENSAJE) : texto;
            segundoPlano.execute(() -> {
                try {
                    bot.atender(telefono, recortado, false);
                } catch (RuntimeException e) {
                    log.error("Error atendiendo a {}", telefono, e);
                }
            });
        } else {
            segundoPlano.execute(() -> bot.atenderNoTexto(telefono));
        }
    }
}
