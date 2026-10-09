package com.ggaming.controller;

import com.ggaming.bot.BotProperties;
import com.ggaming.bot.BotService;
import com.ggaming.bot.TiendaProperties;
import com.ggaming.config.AppProperties;
import com.ggaming.repository.ChatRepository;
import com.ggaming.repository.TurnoRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Panel del bot de WhatsApp en el admin: estado, simulador, turnos y conversaciones.
 * Está bajo /admin, así que pide login (SecurityConfig).
 */
@Controller
@RequestMapping("/admin/bot")
public class BotAdminController {

    private static final String PREFIJO_SIMULADOR = "sim:";

    private final BotService bot;
    private final ChatRepository chats;
    private final TurnoRepository turnos;
    private final BotProperties config;
    private final TiendaProperties tienda;
    private final AppProperties app;

    public BotAdminController(BotService bot, ChatRepository chats, TurnoRepository turnos,
                              BotProperties config, TiendaProperties tienda, AppProperties app) {
        this.bot = bot;
        this.chats = chats;
        this.turnos = turnos;
        this.config = config;
        this.tienda = tienda;
        this.app = app;
    }

    @GetMapping
    public String panel(@RequestParam(required = false) String sim, Model model) {
        String numeroSim = sim == null || sim.isBlank() ? "5493510000000" : sim.replaceAll("\\D", "");
        model.addAttribute("config", config);
        // Qué variables del .env están cargadas (sin mostrar los valores secretos)
        Map<String, Boolean> checklist = new LinkedHashMap<>(); // LinkedHashMap: respeta el orden
        checklist.put("WHATSAPP_TOKEN", BotProperties.lleno(config.token()));
        checklist.put("WHATSAPP_PHONE_NUMBER_ID", BotProperties.lleno(config.phoneNumberId()));
        checklist.put("WHATSAPP_VERIFY_TOKEN", BotProperties.lleno(config.verifyToken()));
        checklist.put("WHATSAPP_APP_SECRET", BotProperties.lleno(config.appSecret()));
        checklist.put("Plantillas de aviso (opcional)",
                BotProperties.lleno(config.plantillaConsulta()) && BotProperties.lleno(config.plantillaTurno()));
        model.addAttribute("checklist", checklist);
        model.addAttribute("urlWebhook", app.baseUrl() + "/webhook/whatsapp");
        model.addAttribute("numeroSim", numeroSim);
        model.addAttribute("conversacionSim", chats.ultimos(PREFIJO_SIMULADOR + numeroSim, 40));
        model.addAttribute("turnos", turnos.proximos(LocalDate.now(tienda.zona())));
        model.addAttribute("conversaciones", chats.recientes(30).stream()
                .filter(c -> !c.telefono().startsWith(PREFIJO_SIMULADOR)).toList());
        return "admin/bot";
    }

    /** Simulador: le "escribe" al bot como si fueras un cliente. No envía nada por WhatsApp. */
    @PostMapping("/simular")
    public String simular(@RequestParam String numero, @RequestParam String mensaje, RedirectAttributes flash) {
        String limpio = numero.replaceAll("\\D", "");
        String texto = mensaje.strip();
        if (limpio.length() < 8 || texto.isEmpty() || texto.length() > 1000) {
            flash.addFlashAttribute("error", "Poné un número válido y un mensaje (máximo 1000 caracteres).");
        } else {
            bot.atender(PREFIJO_SIMULADOR + limpio, texto, true);
        }
        return "redirect:/admin/bot?sim=" + limpio + "#mensaje";
    }

    /** Borra la conversación de prueba para empezar de cero (solo las simuladas). */
    @PostMapping("/simular/reiniciar")
    public String reiniciarSimulacion(@RequestParam String numero) {
        String limpio = numero.replaceAll("\\D", "");
        chats.borrar(PREFIJO_SIMULADOR + limpio);
        return "redirect:/admin/bot?sim=" + limpio + "#mensaje";
    }

    /** Vuelve a poner al bot a atender un chat que estaba derivado al dueño. */
    @PostMapping("/{telefono}/reactivar")
    public String reactivar(@PathVariable String telefono, RedirectAttributes flash) {
        String limpio = telefono.replaceAll("\\D", "");
        chats.reactivarBot(limpio);
        flash.addFlashAttribute("mensaje", "El bot vuelve a atender a +" + limpio + ".");
        return "redirect:/admin/bot";
    }
}
