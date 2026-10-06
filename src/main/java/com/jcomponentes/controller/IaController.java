package com.jcomponentes.controller;

import com.jcomponentes.ia.IaException;
import com.jcomponentes.ia.IaService;
import com.jcomponentes.ia.MensajeChat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * API JSON de la IA para el panel admin (la llama el JavaScript de la página).
 * Está bajo /admin, así que también pide login y token CSRF.
 */
@RestController
@RequestMapping("/admin/ia")
public class IaController {

    private final IaService ia;

    public IaController(IaService ia) {
        this.ia = ia;
    }

    record PedidoSugerencia(@NotBlank @Size(max = 120) String nombre) {}

    record PedidoPregunta(@NotBlank @Size(max = 1000) String pregunta,
                          @Size(max = 40) List<MensajeChat> conversacion) {}

    /** POST /admin/ia/sugerir {"nombre": "RTX 3060 12GB"} */
    @PostMapping("/sugerir")
    public IaService.Sugerencia sugerir(@Valid @RequestBody PedidoSugerencia pedido) {
        return ia.sugerir(pedido.nombre().strip());
    }

    /** POST /admin/ia/preguntar {"pregunta": "...", "conversacion": [...]} */
    @PostMapping("/preguntar")
    public Map<String, String> preguntar(@Valid @RequestBody PedidoPregunta pedido) {
        return Map.of("respuesta", ia.preguntar(pedido.pregunta().strip(), pedido.conversacion()));
    }

    @ExceptionHandler(IaException.class)
    public ResponseEntity<Map<String, String>> errorIa(IaException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> pedidoInvalido() {
        return ResponseEntity.badRequest().body(Map.of("error", "Escribí algo (máximo 120 caracteres para el nombre, 1000 para preguntas)."));
    }
}
