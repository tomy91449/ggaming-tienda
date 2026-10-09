package com.ggaming.bot;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Validación de turnos, formato de los avisos y firma del webhook. */
class AgendaYAvisosTest {

    private final Agenda agenda = new Agenda(LocalTime.of(8, 0), LocalTime.of(21, 0));
    // "Ahora" fijo: viernes 9/10/2026 a las 14:00
    private final LocalDateTime ahora = LocalDateTime.of(2026, 10, 9, 14, 0);

    @Test
    void turnoDentroDelHorarioEsValido() {
        assertThat(agenda.validar("2026-10-10", "18:30", ahora)).isEqualTo(
                new Agenda.Valido(java.time.LocalDate.of(2026, 10, 10), LocalTime.of(18, 30)));
        assertThat(agenda.validar("2026-10-10", "8", ahora)).isInstanceOf(Agenda.Valido.class);
        assertThat(agenda.validar("2026-10-10", "18hs", ahora)).isInstanceOf(Agenda.Valido.class);
    }

    @Test
    void turnoFueraDeHorarioOPasadoEsInvalido() {
        assertThat(agenda.validar("2026-10-10", "23:00", ahora)).isInstanceOf(Agenda.Invalido.class);
        assertThat(agenda.validar("2026-10-10", "07:30", ahora)).isInstanceOf(Agenda.Invalido.class);
        assertThat(agenda.validar("2026-10-10", "21:00", ahora)).isInstanceOf(Agenda.Invalido.class); // cierra a las 21
        assertThat(agenda.validar("2026-10-09", "13:00", ahora)).isInstanceOf(Agenda.Invalido.class); // ya pasó
        assertThat(agenda.validar("2026-10-09", "14:05", ahora)).isInstanceOf(Agenda.Invalido.class); // muy pronto
        assertThat(agenda.validar("2027-01-01", "10:00", ahora)).isInstanceOf(Agenda.Invalido.class); // muy lejos
        assertThat(agenda.validar("mañana", "10:00", ahora)).isInstanceOf(Agenda.Invalido.class);     // no es fecha
        assertThat(agenda.validar("2026-10-10", "tarde", ahora)).isInstanceOf(Agenda.Invalido.class);
    }

    @Test
    void avisosConElFormatoPedido() {
        assertThat(Avisos.consulta("5493511234567", "¿me hacés precio por 2?").lines().findFirst().orElseThrow())
                .isEqualTo("El número: +5493511234567 tiene una consulta que no puedo responder");
        assertThat(Avisos.turno("5493511234567", "18:30", "mañana sábado 10/10", "#1 RTX 3060")
                .lines().findFirst().orElseThrow())
                .isEqualTo("El número: +5493511234567 agendó un horario a las 18:30 hs");
    }

    @Test
    void firmaDelWebhook() {
        byte[] cuerpo = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        String firma = FirmaWebhook.firmar("secreto", cuerpo);
        assertThat(FirmaWebhook.valida("secreto", cuerpo, firma)).isTrue();
        assertThat(FirmaWebhook.valida("otro-secreto", cuerpo, firma)).isFalse();      // secreto equivocado
        assertThat(FirmaWebhook.valida("secreto", "{\"x\":1}".getBytes(StandardCharsets.UTF_8), firma)).isFalse(); // cuerpo alterado
        assertThat(FirmaWebhook.valida("secreto", cuerpo, null)).isFalse();             // sin firma
        assertThat(FirmaWebhook.valida("", cuerpo, firma)).isFalse();                   // sin App Secret configurado
        assertThat(FirmaWebhook.valida("secreto", cuerpo, "sha256=zz")).isFalse();      // basura
    }
}
