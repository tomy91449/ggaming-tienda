package com.ggaming.repository;

import com.ggaming.model.ChatLinea;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** REPOSITORIO de las conversaciones del bot (tablas chat_mensaje y chat_estado). */
@Repository
public class ChatRepository {

    /** Resumen de una conversación para el panel admin. */
    public record Conversacion(String telefono, String ultimoTexto, String ultimoRol, Instant ultimaFecha,
                               String modo, Instant pausadoHasta) {}

    private final JdbcTemplate jdbc;

    public ChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ChatLinea> MAPPER = (rs, i) -> new ChatLinea(
            rs.getString("telefono"), rs.getString("rol"), rs.getString("texto"),
            Instant.parse(rs.getString("fecha")));

    public void guardar(String telefono, String rol, String texto) {
        jdbc.update("INSERT INTO chat_mensaje (telefono, rol, texto, fecha) VALUES (?, ?, ?, ?)",
                telefono, rol, texto, Instant.now().toString());
    }

    /** Los últimos N mensajes de un chat, del más viejo al más nuevo. */
    public List<ChatLinea> ultimos(String telefono, int cantidad) {
        List<ChatLinea> lista = new ArrayList<>(jdbc.query(
                "SELECT * FROM chat_mensaje WHERE telefono = ? ORDER BY id DESC LIMIT ?", MAPPER, telefono, cantidad));
        java.util.Collections.reverse(lista);
        return lista;
    }

    /** Cuántos mensajes mandó el cliente desde cierto momento (para el límite anti-spam). */
    public int mensajesDelClienteDesde(String telefono, Instant desde) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_mensaje WHERE telefono = ? AND rol = 'CLIENTE' AND fecha >= ?",
                Integer.class, telefono, desde.toString());
    }

    /** Modo HUMANO activo hasta esa fecha (vacío si el bot atiende normalmente). */
    public Optional<Instant> pausadoHasta(String telefono) {
        List<String> r = jdbc.queryForList(
                "SELECT pausado_hasta FROM chat_estado WHERE telefono = ? AND modo = 'HUMANO'", String.class, telefono);
        return r.isEmpty() || r.get(0) == null ? Optional.empty() : Optional.of(Instant.parse(r.get(0)));
    }

    public void pasarAHumano(String telefono, Instant hasta) {
        jdbc.update("INSERT INTO chat_estado (telefono, modo, pausado_hasta) VALUES (?, 'HUMANO', ?) "
                + "ON CONFLICT(telefono) DO UPDATE SET modo = 'HUMANO', pausado_hasta = excluded.pausado_hasta",
                telefono, hasta.toString());
    }

    public void reactivarBot(String telefono) {
        jdbc.update("INSERT INTO chat_estado (telefono, modo, pausado_hasta) VALUES (?, 'BOT', NULL) "
                + "ON CONFLICT(telefono) DO UPDATE SET modo = 'BOT', pausado_hasta = NULL", telefono);
    }

    /** Borra una conversación y su estado (lo usa el simulador para empezar de cero). */
    public void borrar(String telefono) {
        jdbc.update("DELETE FROM chat_mensaje WHERE telefono = ?", telefono);
        jdbc.update("DELETE FROM chat_estado WHERE telefono = ?", telefono);
    }

    /** Las conversaciones más recientes, con su último mensaje y su estado. */
    public List<Conversacion> recientes(int cantidad) {
        return jdbc.query("""
                SELECT m.telefono, m.texto, m.rol, m.fecha, e.modo, e.pausado_hasta
                FROM chat_mensaje m
                JOIN (SELECT telefono, MAX(id) AS ultimo FROM chat_mensaje GROUP BY telefono) u ON m.id = u.ultimo
                LEFT JOIN chat_estado e ON e.telefono = m.telefono
                ORDER BY m.id DESC LIMIT ?""",
                (rs, i) -> new Conversacion(
                        rs.getString("telefono"), rs.getString("texto"), rs.getString("rol"),
                        Instant.parse(rs.getString("fecha")),
                        rs.getString("modo") == null ? "BOT" : rs.getString("modo"),
                        rs.getString("pausado_hasta") == null ? null : Instant.parse(rs.getString("pausado_hasta"))),
                cantidad);
    }
}
