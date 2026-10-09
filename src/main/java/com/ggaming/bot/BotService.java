package com.ggaming.bot;

import com.ggaming.model.ChatLinea;
import com.ggaming.model.Producto;
import com.ggaming.repository.ChatRepository;
import com.ggaming.repository.ProductoRepository;
import com.ggaming.repository.TurnoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SERVICIO del bot de WhatsApp: recibe cada mensaje de un cliente y decide qué hacer.
 *
 * Flujo:
 *  1. Guarda el mensaje (memoria de la conversación).
 *  2. Filtros: anti-spam y "modo humano" (si te derivó el chat, el bot calla).
 *  3. Le pide a la IA una decisión (responder / derivar / agendar).
 *  4. El CÓDIGO valida y ejecuta: responde al cliente y, si corresponde, te avisa.
 *
 * Importante: no usa @Transactional a propósito. La IA tarda varios segundos y la base
 * (SQLite) tiene una sola conexión: no queremos tenerla tomada mientras esperamos.
 */
@Service
public class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);
    private static final int MENSAJES_DE_MEMORIA = 20;
    private static final int MAX_PRODUCTOS_EN_CONTEXTO = 200;
    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    /** Si la IA falla, el bot calla poco: el cliente no queda horas sin respuesta. */
    static final Duration PAUSA_SI_FALLA_LA_IA = Duration.ofMinutes(30);

    static final String RESPUESTA_SIN_IA = "¡Hola! Gracias por escribir a GGaming 🙌 En este momento no puedo "
            + "responderte, pero ya le pasé tu consulta al dueño y te escribe por acá a la brevedad.";
    static final String RESPUESTA_NO_TEXTO = "¡Hola! Por ahora solo puedo leer mensajes de texto 🙂 ¿Me lo escribís?";

    /** Lo que pasó con un mensaje (lo usa el simulador del admin para mostrarlo). */
    public record Resultado(String respuesta, List<String> avisos) {
        static Resultado silencio() {
            return new Resultado(null, List.of());
        }
    }

    private final AgenteBot agente;
    private final Mensajero mensajero;
    private final ChatRepository chats;
    private final TurnoRepository turnos;
    private final ProductoRepository productos;
    private final BotProperties config;
    private final TiendaProperties tienda;
    private final Agenda agenda;
    private final Clock reloj;
    private final Map<String, Object> candados = new ConcurrentHashMap<>();

    @Autowired
    public BotService(AgenteBot agente, Mensajero mensajero, ChatRepository chats, TurnoRepository turnos,
                      ProductoRepository productos, BotProperties config, TiendaProperties tienda) {
        this(agente, mensajero, chats, turnos, productos, config, tienda, Clock.system(tienda.zona()));
    }

    /** Constructor con reloj configurable (en los tests, "ahora" es una fecha fija). */
    BotService(AgenteBot agente, Mensajero mensajero, ChatRepository chats, TurnoRepository turnos,
               ProductoRepository productos, BotProperties config, TiendaProperties tienda, Clock reloj) {
        this.agente = agente;
        this.mensajero = mensajero;
        this.chats = chats;
        this.turnos = turnos;
        this.productos = productos;
        this.config = config;
        this.tienda = tienda;
        this.agenda = new Agenda(tienda.horaApertura(), tienda.horaCierre());
        this.reloj = reloj.withZone(tienda.zona());
    }

    // ================================================================ entrada

    /**
     * Atiende un mensaje de texto.
     *
     * @param simulado true desde el simulador del admin: no envía nada por WhatsApp de verdad
     */
    public Resultado atender(String telefono, String texto, boolean simulado) {
        // De a un mensaje por cliente: si manda 3 seguidos, se responden en orden.
        synchronized (candados.computeIfAbsent(telefono, t -> new Object())) {
            if (telefono.equals(config.numeroDueno())) {
                return atenderAlDueno(texto, simulado);
            }
            chats.guardar(telefono, ChatLinea.CLIENTE, texto);

            Instant ahora = reloj.instant();
            if (chats.mensajesDelClienteDesde(telefono, ahora.minus(Duration.ofHours(1))) > config.maxMensajesPorHora()) {
                log.warn("Anti-spam: {} superó {} mensajes por hora, se ignora.", telefono, config.maxMensajesPorHora());
                return Resultado.silencio();
            }
            var pausa = chats.pausadoHasta(telefono);
            if (pausa.isPresent()) {
                if (pausa.get().isAfter(ahora)) return Resultado.silencio(); // lo atiende el dueño
                chats.reactivarBot(telefono);                                 // ya pasó la pausa
            }

            List<ChatLinea> historial = chats.ultimos(telefono, MENSAJES_DE_MEMORIA + 1);
            historial = historial.subList(0, Math.max(0, historial.size() - 1)); // sin el mensaje actual

            DecisionBot decision;
            try {
                decision = agente.decidir(historial, texto, contexto());
            } catch (RuntimeException e) {
                log.warn("La IA del bot falló para {}: {}", telefono, e.getMessage());
                // Falla de la IA: pausa corta. Si no le contestás vos, el bot retoma enseguida.
                return derivar(telefono, texto, RESPUESTA_SIN_IA, simulado, PAUSA_SI_FALLA_LA_IA);
            }
            return switch (decision.accionNormalizada()) {
                case DecisionBot.DERIVAR -> derivar(telefono, texto, decision.respuesta(), simulado,
                        Duration.ofHours(config.horasPausa()));
                case DecisionBot.AGENDAR -> agendar(telefono, decision, simulado);
                default -> {
                    responder(telefono, decision.respuesta(), simulado);
                    yield new Resultado(decision.respuesta(), List.of());
                }
            };
        }
    }

    /** Audios, fotos, stickers...: por ahora el bot solo lee texto. */
    public void atenderNoTexto(String telefono) {
        if (telefono.equals(config.numeroDueno())) return;
        var pausa = chats.pausadoHasta(telefono);
        if (pausa.isPresent() && pausa.get().isAfter(reloj.instant())) return;
        responder(telefono, RESPUESTA_NO_TEXTO, false);
    }

    // ================================================================ acciones

    private Resultado derivar(String telefono, String ultimoMensaje, String respuesta, boolean simulado,
                              Duration pausa) {
        responder(telefono, respuesta, simulado);
        String aviso = Avisos.consulta(telefono, ultimoMensaje);
        avisarAlDueno(telefono, aviso, config.plantillaConsulta(), List.of(Avisos.legible(telefono)), simulado);
        chats.pasarAHumano(telefono, reloj.instant().plus(pausa));
        return new Resultado(respuesta, List.of(aviso));
    }

    private Resultado agendar(String telefono, DecisionBot d, boolean simulado) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Agenda.Resultado validacion = agenda.validar(d.dia(), d.hora(), ahora);

        if (validacion instanceof Agenda.Invalido invalido) {
            String respuesta = invalido.motivo() + " Atendemos todos los días de " + tienda.apertura()
                    + " a " + tienda.cierre() + " hs. ¿Qué otro día y horario te queda bien?";
            responder(telefono, respuesta, simulado);
            return new Resultado(respuesta, List.of());
        }
        var turno = (Agenda.Valido) validacion;
        String hora = turno.hora().format(DateTimeFormatter.ofPattern("HH:mm"));
        String dia = diaLegible(turno.dia(), ahora.toLocalDate());
        String productosTexto = d.productos() == null ? "" : String.join(", ", d.productos());
        if (productosTexto.length() > 300) productosTexto = productosTexto.substring(0, 300);

        turnos.insertar(telefono, turno.dia(), turno.hora(), productosTexto);

        // La confirmación la arma el código (no la IA): dirección, día y hora siempre correctos.
        String respuesta = "¡Listo! ✅ Te agendé para *" + dia + " a las " + hora + " hs*.\n"
                + "📍 " + tienda.direccion() + "\n"
                + "🗺️ Cómo llegar: " + tienda.urlComoLlegar() + "\n"
                + "Podés probar el producto antes de comprarlo. Pagás con " + tienda.pagos().toLowerCase(ES_AR) + ".\n"
                + "¡Te esperamos!";
        responder(telefono, respuesta, simulado);

        String aviso = Avisos.turno(telefono, hora, dia, productosTexto);
        avisarAlDueno(telefono, aviso, config.plantillaTurno(), List.of(Avisos.legible(telefono), hora), simulado);
        return new Resultado(respuesta, List.of(aviso));
    }

    /** El dueño también puede escribirle al bot: "activar 5493511234567" reactiva un chat derivado. */
    private Resultado atenderAlDueno(String texto, boolean simulado) {
        String limpio = texto.strip().toLowerCase(ES_AR);
        String respuesta;
        if (limpio.startsWith("activar")) {
            String numero = limpio.replaceAll("\\D", "");
            if (numero.length() < 8) {
                respuesta = "Pasame el número completo, ej: activar 5493511234567";
            } else {
                chats.reactivarBot(numero);
                respuesta = "Listo, el bot vuelve a atender a " + Avisos.legible(numero) + ".";
            }
        } else {
            respuesta = "Hola 👋 Soy el bot de GGaming. Para que vuelva a atender un chat que te derivé, "
                    + "escribime: activar NÚMERO (ej: activar 5493511234567).";
        }
        if (!simulado) enviarSeguro(config.numeroDueno(), respuesta);
        return new Resultado(respuesta, List.of());
    }

    // ================================================================ envío

    private void responder(String telefono, String texto, boolean simulado) {
        chats.guardar(telefono, ChatLinea.BOT, texto);
        if (!simulado) enviarSeguro(telefono, texto);
    }

    /**
     * Avisa al dueño. Con plantilla aprobada llega siempre; con texto común, solo si
     * el dueño le escribió al bot en las últimas 24 hs (regla de WhatsApp).
     */
    private void avisarAlDueno(String telefonoCliente, String aviso, String plantilla,
                               List<String> parametros, boolean simulado) {
        chats.guardar(telefonoCliente, ChatLinea.AVISO, aviso);
        if (simulado) return;
        try {
            if (BotProperties.lleno(plantilla)) {
                mensajero.enviarPlantilla(config.numeroDueno(), plantilla, parametros);
            } else {
                mensajero.enviarTexto(config.numeroDueno(), aviso);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar al dueño: {}", e.getMessage());
            chats.guardar(telefonoCliente, ChatLinea.AVISO, "⚠️ No se pudo enviar el aviso: " + e.getMessage());
        }
    }

    private void enviarSeguro(String para, String texto) {
        try {
            mensajero.enviarTexto(para, texto);
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el mensaje a {}: {}", para, e.getMessage());
        }
    }

    // ================================================================ contexto para la IA

    /** Fecha y hora actual + datos de la tienda + productos en stock, como texto para la IA. */
    String contexto() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        NumberFormat pesos = NumberFormat.getIntegerInstance(ES_AR);
        StringBuilder sb = new StringBuilder();
        sb.append("AHORA: ").append(ahora.format(DateTimeFormatter.ofPattern("EEEE d/M/yyyy HH:mm", ES_AR)))
          .append(" (hora de Córdoba). HOY = ").append(ahora.toLocalDate()).append('\n');
        sb.append("\nDATOS DE LA TIENDA:\n")
          .append("- Dirección: ").append(tienda.direccion()).append('\n')
          .append("- Ubicación en Google Maps: ").append(tienda.urlMapa()).append('\n')
          .append("- Horario de atención (visitas y retiro): todos los días de ").append(tienda.apertura())
          .append(" a ").append(tienda.cierre()).append(" hs\n")
          .append("- Medios de pago: ").append(tienda.pagos()).append('\n')
          .append("- Entrega: ").append(tienda.entrega()).append('\n')
          .append("- Garantía: ").append(tienda.garantia()).append('\n');

        sb.append("\nPRODUCTOS EN STOCK (#número | nombre | categoría | precio | detalle):\n");
        List<Producto> enStock = new ArrayList<>(productos.buscar(null, null).stream()
                .filter(p -> !p.vendido()).limit(MAX_PRODUCTOS_EN_CONTEXTO).toList());
        if (enStock.isEmpty()) sb.append("(no hay productos en stock en este momento)\n");
        for (Producto p : enStock) {
            String detalle = p.descripcion() == null ? "" : p.descripcion().replaceAll("\\s+", " ");
            if (detalle.length() > 140) detalle = detalle.substring(0, 140) + "…";
            sb.append("- #").append(p.id()).append(" | ").append(p.nombre()).append(" | ")
              .append(p.categoria().getEtiqueta()).append(" | $").append(pesos.format(p.precio()))
              .append(" | ").append(detalle).append('\n');
        }
        return sb.toString();
    }

    /** "hoy viernes 9/10", "mañana sábado 10/10" o "domingo 11/10". */
    static String diaLegible(LocalDate dia, LocalDate hoy) {
        String base = dia.format(DateTimeFormatter.ofPattern("EEEE d/M", ES_AR));
        if (dia.equals(hoy)) return "hoy " + base;
        if (dia.equals(hoy.plusDays(1))) return "mañana " + base;
        return base;
    }
}
