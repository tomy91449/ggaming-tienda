package com.ggaming.bot;

import com.ggaming.model.Categoria;
import com.ggaming.model.Producto;
import com.ggaming.repository.ChatRepository;
import com.ggaming.repository.ProductoRepository;
import com.ggaming.repository.TurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/**
 * Tests de la lógica del bot con una IA y un WhatsApp FALSOS (Mockito):
 * le decimos qué "decide" la IA y verificamos qué hace nuestro código.
 */
class BotServiceTest {

    private static final String CLIENTE = "5493511234567";
    private static final String DUENO = "5493834507890";
    private static final ZoneId CORDOBA = ZoneId.of("America/Argentina/Cordoba");
    // "Ahora" fijo: viernes 9/10/2026 14:00 en Córdoba
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-09T17:00:00Z"), CORDOBA);

    private AgenteBot agente;
    private Mensajero mensajero;
    private ChatRepository chats;
    private TurnoRepository turnos;
    private BotService bot;

    @BeforeEach
    void preparar() {
        agente = mock(AgenteBot.class);
        mensajero = mock(Mensajero.class);
        chats = mock(ChatRepository.class);
        turnos = mock(TurnoRepository.class);
        ProductoRepository productos = mock(ProductoRepository.class);
        when(productos.buscar(null, null)).thenReturn(List.of(
                new Producto(1L, "NVIDIA RTX 3060 12GB", "MSI Ventus", Categoria.PLACA_VIDEO, 380000, false, null)));
        when(chats.ultimos(anyString(), anyInt())).thenReturn(List.of());
        when(chats.pausadoHasta(anyString())).thenReturn(Optional.empty());
        bot = crear(config(""), productos);
    }

    private BotService crear(BotProperties config, ProductoRepository productos) {
        return new BotService(agente, mensajero, chats, turnos, productos, config,
                new TiendaProperties(null, null, null, null, null, null, null, null), RELOJ);
    }

    private static BotProperties config(String plantillas) {
        return new BotProperties("token", "123", "verif", "secreto", DUENO, "http://x", plantillas, plantillas,
                "es_AR", 12, 30);
    }

    private void iaDecide(String accion, String dia, String hora) {
        when(agente.decidir(any(), anyString(), anyString())).thenReturn(
                new DecisionBot("respuesta de la IA", accion, dia, hora, List.of("#1 NVIDIA RTX 3060 12GB"), ""));
    }

    @Test
    void preguntaComunSoloResponde() {
        iaDecide("NINGUNA", "", "");
        var r = bot.atender(CLIENTE, "¿tenés placas?", false);
        verify(mensajero).enviarTexto(CLIENTE, "respuesta de la IA");
        verify(mensajero, never()).enviarTexto(eq(DUENO), anyString());
        assertThat(r.avisos()).isEmpty();
    }

    @Test
    void turnoValidoSeGuardaYAvisaAlDuenoConElFormatoPedido() {
        iaDecide("AGENDAR", "2026-10-10", "18:30");
        var r = bot.atender(CLIENTE, "mañana a las 18:30", false);
        verify(turnos).insertar(CLIENTE, LocalDate.of(2026, 10, 10), LocalTime.of(18, 30), "#1 NVIDIA RTX 3060 12GB");
        verify(mensajero).enviarTexto(eq(DUENO), startsWith("El número: +5493511234567 agendó un horario a las 18:30 hs"));
        verify(mensajero).enviarTexto(eq(CLIENTE), startsWith("¡Listo! ✅ Te agendé para *mañana sábado 10/10 a las 18:30 hs*"));
        assertThat(r.respuesta()).contains("Blvr. Chacabuco 651")
                .contains("https://www.google.com/maps/dir/?api=1&destination=Blvr.+Chacabuco+651");
    }

    @Test
    void turnoFueraDeHorarioNoSeGuardaNiAvisa() {
        iaDecide("AGENDAR", "2026-10-10", "23:00");
        var r = bot.atender(CLIENTE, "mañana a las 23", false);
        verify(turnos, never()).insertar(any(), any(), any(), any());
        verify(mensajero, never()).enviarTexto(eq(DUENO), anyString());
        assertThat(r.respuesta()).contains("fuera de nuestro horario").contains("08:00 a 21:00");
    }

    @Test
    void derivarAvisaAlDuenoYPausaElBot() {
        iaDecide("DERIVAR", "", "");
        bot.atender(CLIENTE, "¿me hacés precio por 3?", false);
        verify(mensajero).enviarTexto(eq(DUENO), startsWith("El número: +5493511234567 tiene una consulta que no puedo responder"));
        verify(chats).pasarAHumano(eq(CLIENTE), eq(Instant.parse("2026-10-10T05:00:00Z"))); // ahora + 12 hs
    }

    @Test
    void siLaIaFallaElClienteIgualRecibeRespuestaYVosElAviso() {
        when(agente.decidir(any(), anyString(), anyString())).thenThrow(new RuntimeException("Gemini caído"));
        var r = bot.atender(CLIENTE, "hola", false);
        verify(mensajero).enviarTexto(CLIENTE, BotService.RESPUESTA_SIN_IA);
        verify(mensajero).enviarTexto(eq(DUENO), startsWith("El número: +5493511234567 tiene una consulta"));
        assertThat(r.avisos()).hasSize(1);
        // Pausa corta (30 min), no 12 hs: si no contestás vos, el bot retoma pronto
        verify(chats).pasarAHumano(CLIENTE, Instant.parse("2026-10-09T17:30:00Z"));
    }

    @Test
    void chatDerivadoNoLoContestaElBot() {
        when(chats.pausadoHasta(CLIENTE)).thenReturn(Optional.of(Instant.parse("2026-10-09T20:00:00Z")));
        bot.atender(CLIENTE, "¿y entonces?", false);
        verify(agente, never()).decidir(any(), anyString(), anyString());
        verify(mensajero, never()).enviarTexto(anyString(), anyString());
    }

    @Test
    void elDuenoReactivaUnChatPorWhatsapp() {
        bot.atender(DUENO, "activar 5493511234567", false);
        verify(chats).reactivarBot("5493511234567");
        verify(agente, never()).decidir(any(), anyString(), anyString());
    }

    @Test
    void elSimuladorNoEnviaNadaPorWhatsapp() {
        iaDecide("AGENDAR", "2026-10-10", "18:30");
        var r = bot.atender("sim:" + CLIENTE, "mañana 18:30", true);
        verifyNoInteractions(mensajero);
        assertThat(r.avisos()).singleElement().asString().startsWith("El número: +5493511234567 (prueba) agendó");
    }

    @Test
    void conPlantillasAprobadasUsaPlantilla() {
        ProductoRepository productos = mock(ProductoRepository.class);
        when(productos.buscar(null, null)).thenReturn(List.of());
        BotService conPlantillas = crear(config("aviso_ggaming"), productos);
        iaDecide("AGENDAR", "2026-10-10", "18:30");
        conPlantillas.atender(CLIENTE, "mañana 18:30", false);
        verify(mensajero).enviarPlantilla(DUENO, "aviso_ggaming", List.of("+5493511234567", "18:30"));
    }

    @Test
    void elContextoSoloTieneProductosEnStock() {
        String contexto = bot.contexto();
        assertThat(contexto).contains("HOY = 2026-10-09").contains("#1 | NVIDIA RTX 3060 12GB")
                .contains("$380.000").contains("Blvr. Chacabuco 651").contains("08:00 a 21:00");
    }
}
