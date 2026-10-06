package com.jcomponentes.service;

import com.jcomponentes.model.Categoria;
import com.jcomponentes.model.Producto;
import com.jcomponentes.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SERVICIO: acá viven las reglas del negocio. El controlador le pide cosas
 * al servicio, y el servicio decide qué pedirle al repositorio.
 *
 * En las fases 2 y 3 acá va la lógica de memoria de precios y el resumen automático.
 */
@Service
public class CatalogoService {

    /** Nadie busca hardware con más de 60 letras; cortar evita abusos (búsquedas gigantes). */
    public static final int MAX_BUSQUEDA = 60;

    private final ProductoRepository productos;

    public CatalogoService(ProductoRepository productos) {
        this.productos = productos;
    }

    public List<Producto> buscar(String texto, Categoria categoria) {
        return productos.buscar(normalizarBusqueda(texto), categoria);
    }

    /** Quita espacios de los costados, corta el largo y convierte "" en null (sin filtro). */
    public String normalizarBusqueda(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() > MAX_BUSQUEDA ? limpio.substring(0, MAX_BUSQUEDA) : limpio;
    }
}
