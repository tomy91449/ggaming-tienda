package com.jcomponentes.repository;

import com.jcomponentes.model.Foto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** REPOSITORIO de la tabla "foto": solo SQL, sin reglas de negocio. */
@Repository
public class FotoRepository {

    private final JdbcTemplate jdbc;

    public FotoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Foto> MAPPER = (rs, i) -> new Foto(
            rs.getLong("id"),
            rs.getLong("producto_id"),
            rs.getString("ruta"),
            rs.getInt("orden")
    );

    public List<Foto> listarPorProducto(long productoId) {
        return jdbc.query("SELECT * FROM foto WHERE producto_id = ? ORDER BY orden, id", MAPPER, productoId);
    }

    public Optional<Foto> buscarPorId(long id) {
        return jdbc.query("SELECT * FROM foto WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public int contar(long productoId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM foto WHERE producto_id = ?", Integer.class, productoId);
    }

    public void insertar(long productoId, String ruta, int orden) {
        jdbc.update("INSERT INTO foto (producto_id, ruta, orden) VALUES (?, ?, ?)", productoId, ruta, orden);
    }

    public void borrar(long id) {
        jdbc.update("DELETE FROM foto WHERE id = ?", id);
    }

    public void borrarPorProducto(long productoId) {
        jdbc.update("DELETE FROM foto WHERE producto_id = ?", productoId);
    }
}
