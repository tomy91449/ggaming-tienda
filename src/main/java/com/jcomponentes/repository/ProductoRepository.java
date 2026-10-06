package com.jcomponentes.repository;

import com.jcomponentes.model.Categoria;
import com.jcomponentes.model.Producto;
import com.jcomponentes.model.ProductoForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REPOSITORIO: la única clase que habla con la tabla "producto".
 * Si mañana cambiamos SQLite por PostgreSQL, solo se toca acá.
 */
@Repository
public class ProductoRepository {

    private final JdbcTemplate jdbc;

    // Spring nos "inyecta" el JdbcTemplate ya configurado con la base.
    public ProductoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Convierte una fila del resultado SQL en un objeto Producto.
    private static final RowMapper<Producto> MAPPER = (rs, i) -> new Producto(
            rs.getLong("id"),
            rs.getString("nombre"),
            rs.getString("descripcion"),
            Categoria.valueOf(rs.getString("categoria")),
            rs.getLong("precio"),
            rs.getBoolean("vendido"),
            rs.getString("foto")
    );

    /**
     * Busca productos por texto (en nombre o descripción) y/o categoría.
     * Ambos filtros son opcionales. Usamos "?" en vez de concatenar el texto
     * del usuario para evitar inyección SQL.
     */
    public List<Producto> buscar(String texto, Categoria categoria) {
        StringBuilder sql = new StringBuilder("SELECT * FROM producto WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (texto != null && !texto.isBlank()) {
            // ESCAPE: si el usuario escribe % o _ se buscan como letras,
            // no como comodines de SQL (sino "%" devolvería todo).
            sql.append(" AND (nombre LIKE ? ESCAPE '\\' OR descripcion LIKE ? ESCAPE '\\')");
            String like = "%" + escaparLike(texto.trim()) + "%";
            params.add(like);
            params.add(like);
        }
        if (categoria != null) {
            sql.append(" AND categoria = ?");
            params.add(categoria.name());
        }
        // Primero los disponibles, después los más nuevos.
        sql.append(" ORDER BY vendido ASC, id DESC");

        return jdbc.query(sql.toString(), MAPPER, params.toArray());
    }

    /** Antepone "\" a los comodines de LIKE para que valgan como texto literal. */
    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public Optional<Producto> buscarPorId(long id) {
        return jdbc.query("SELECT * FROM producto WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /**
     * Inserta y devuelve el id nuevo. last_insert_rowid() es de SQLite y es
     * seguro acá porque el pool tiene una sola conexión y el servicio usa @Transactional.
     */
    public long insertar(ProductoForm f) {
        jdbc.update("INSERT INTO producto (nombre, descripcion, categoria, precio, vendido) VALUES (?, ?, ?, ?, ?)",
                f.getNombre(), f.getDescripcion(), f.getCategoria().name(), f.getPrecio(), f.isVendido());
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public void actualizar(long id, ProductoForm f) {
        jdbc.update("UPDATE producto SET nombre = ?, descripcion = ?, categoria = ?, precio = ?, vendido = ? WHERE id = ?",
                f.getNombre(), f.getDescripcion(), f.getCategoria().name(), f.getPrecio(), f.isVendido(), id);
    }

    public void cambiarVendido(long id, boolean vendido) {
        jdbc.update("UPDATE producto SET vendido = ? WHERE id = ?", vendido, id);
    }

    /** La foto principal es la que se ve en la tarjeta del catálogo (null = sin foto). */
    public void ponerFotoPrincipal(long id, String ruta) {
        jdbc.update("UPDATE producto SET foto = ? WHERE id = ?", ruta, id);
    }

    public void borrar(long id) {
        jdbc.update("DELETE FROM producto WHERE id = ?", id);
    }
}
