package com.ggaming.repository;

import com.ggaming.model.Turno;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** REPOSITORIO de los turnos agendados por el bot. */
@Repository
public class TurnoRepository {

    private final JdbcTemplate jdbc;

    public TurnoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(String telefono, LocalDate dia, LocalTime hora, String productos) {
        jdbc.update("INSERT INTO turno (telefono, dia, hora, productos, creado) VALUES (?, ?, ?, ?, ?)",
                telefono, dia.toString(), hora.toString(), productos, Instant.now().toString());
    }

    /** Turnos de hoy en adelante, ordenados por día y hora. */
    public List<Turno> proximos(LocalDate desde) {
        return jdbc.query("SELECT * FROM turno WHERE dia >= ? ORDER BY dia, hora", (rs, i) -> new Turno(
                rs.getLong("id"), rs.getString("telefono"),
                LocalDate.parse(rs.getString("dia")), LocalTime.parse(rs.getString("hora")),
                rs.getString("productos")), desde.toString());
    }
}
