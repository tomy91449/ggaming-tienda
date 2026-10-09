package com.ggaming.model;

import java.time.LocalDate;
import java.time.LocalTime;

/** Una visita agendada por el bot para probar y retirar productos. */
public record Turno(Long id, String telefono, LocalDate dia, LocalTime hora, String productos) {
}
