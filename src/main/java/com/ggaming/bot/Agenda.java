package com.ggaming.bot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida los turnos que propone la IA. La IA puede equivocarse (o el cliente
 * pedir las 3 de la mañana): el código es el que decide si el turno es válido.
 */
public class Agenda {

    /** Hasta cuántos días adelante se puede agendar. */
    static final int MAX_DIAS = 30;
    /** Margen mínimo para un turno en el día (no se puede agendar "en 5 minutos"). */
    static final int MINUTOS_MINIMOS = 15;

    private static final Pattern HORA = Pattern.compile("^(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:hs?)?$");

    public sealed interface Resultado permits Valido, Invalido {}
    public record Valido(LocalDate dia, LocalTime hora) implements Resultado {}
    public record Invalido(String motivo) implements Resultado {}

    private final LocalTime apertura;
    private final LocalTime cierre;

    public Agenda(LocalTime apertura, LocalTime cierre) {
        this.apertura = apertura;
        this.cierre = cierre;
    }

    /**
     * @param dia   "2026-10-10"
     * @param hora  "18:30", "18", "18hs"
     * @param ahora fecha y hora actual en la zona de la tienda
     */
    public Resultado validar(String dia, String hora, LocalDateTime ahora) {
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(dia == null ? "" : dia.strip());
        } catch (DateTimeParseException e) {
            return new Invalido("No entendí qué día querés venir.");
        }
        LocalTime horario = leerHora(hora);
        if (horario == null) {
            return new Invalido("No entendí a qué hora querés venir.");
        }
        if (horario.isBefore(apertura) || !horario.isBefore(cierre)) {
            return new Invalido("Ese horario está fuera de nuestro horario de atención.");
        }
        LocalDateTime turno = LocalDateTime.of(fecha, horario);
        if (turno.isBefore(ahora.plusMinutes(MINUTOS_MINIMOS))) {
            return new Invalido("Ese horario ya pasó o es muy pronto.");
        }
        if (fecha.isAfter(ahora.toLocalDate().plusDays(MAX_DIAS))) {
            return new Invalido("Solo agendamos hasta " + MAX_DIAS + " días adelante.");
        }
        return new Valido(fecha, horario);
    }

    static LocalTime leerHora(String hora) {
        if (hora == null) return null;
        Matcher m = HORA.matcher(hora.strip().toLowerCase());
        if (!m.matches()) return null;
        int h = Integer.parseInt(m.group(1));
        int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (h > 23 || min > 59) return null;
        return LocalTime.of(h, min);
    }
}
