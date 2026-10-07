package com.ggaming.repository;

import com.ggaming.model.Categoria;
import com.ggaming.model.Venta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/** REPOSITORIO del historial de ventas. */
@Repository
public class VentaRepository {

    private final JdbcTemplate jdbc;

    public VentaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Venta> MAPPER = (rs, i) -> {
        // producto_id puede ser NULL: getLong devuelve 0 y wasNull() nos dice si era NULL.
        long productoId = rs.getLong("producto_id");
        return new Venta(
                rs.getLong("id"),
                rs.wasNull() ? null : productoId,
                rs.getString("nombre"),
                Categoria.valueOf(rs.getString("categoria")),
                rs.getLong("precio_publicado"),
                rs.getLong("precio_venta"),
                LocalDate.parse(rs.getString("fecha")));
    };

    /** Todas las ventas, de la más nueva a la más vieja. */
    public List<Venta> listar() {
        return jdbc.query("SELECT * FROM venta ORDER BY fecha DESC, id DESC", MAPPER);
    }

    public void insertar(long productoId, String nombre, Categoria categoria,
                         long precioPublicado, long precioVenta, LocalDate fecha) {
        jdbc.update("INSERT INTO venta (producto_id, nombre, categoria, precio_publicado, precio_venta, fecha) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                productoId, nombre, categoria.name(), precioPublicado, precioVenta, fecha.toString());
    }

    /** Para "deshacer" una venta (si lo marcaste vendido por error). */
    public void borrarPorProducto(long productoId) {
        jdbc.update("DELETE FROM venta WHERE producto_id = ?", productoId);
    }
}
