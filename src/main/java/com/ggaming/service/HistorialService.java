package com.ggaming.service;

import com.ggaming.model.Coincidencia;
import com.ggaming.model.Producto;
import com.ggaming.model.Venta;
import com.ggaming.repository.ProductoRepository;
import com.ggaming.repository.VentaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MEMORIA DE PRECIOS: dado un nombre, busca en TU historial
 * (ventas registradas + productos publicados) los iguales o parecidos.
 *
 * Esto no usa IA: es una comparación local, rápida, gratis y siempre disponible.
 * Después, su resultado se le pasa a la IA para que recomiende un precio.
 */
@Service
public class HistorialService {

    private static final int MAX_RESULTADOS = 8;

    private final VentaRepository ventas;
    private final ProductoRepository productos;
    private final SimilitudService similitud;

    public HistorialService(VentaRepository ventas, ProductoRepository productos, SimilitudService similitud) {
        this.ventas = ventas;
        this.productos = productos;
        this.similitud = similitud;
    }

    public List<Coincidencia> buscarParecidos(String nombre) {
        List<Coincidencia> encontradas = new ArrayList<>();
        Set<Long> productosConVenta = new HashSet<>();

        for (Venta v : ventas.listar()) {
            if (v.productoId() != null) productosConVenta.add(v.productoId());
            var r = similitud.comparar(nombre, v.nombre());
            if (r.puntaje() >= SimilitudService.UMBRAL_PARECIDO || r.mismoModelo()) {
                encontradas.add(new Coincidencia("VENDIDO", v.nombre(), v.precioPublicado(),
                        v.precioVenta(), v.fecha(), r.puntaje(), r.mismoModelo()));
            }
        }
        for (Producto p : productos.buscar(null, null)) {
            if (productosConVenta.contains(p.id())) continue; // ya figura como venta
            var r = similitud.comparar(nombre, p.nombre());
            if (r.puntaje() >= SimilitudService.UMBRAL_PARECIDO || r.mismoModelo()) {
                encontradas.add(new Coincidencia(p.vendido() ? "VENDIDO" : "PUBLICADO", p.nombre(),
                        p.precio(), null, null, r.puntaje(), r.mismoModelo()));
            }
        }

        // Primero el mismo modelo, después por parecido, y a igualdad, lo más reciente.
        encontradas.sort(Comparator.comparing(Coincidencia::mismoModelo).reversed()
                .thenComparing(Comparator.comparingDouble(Coincidencia::similitud).reversed())
                .thenComparing(Coincidencia::fecha, Comparator.nullsLast(Comparator.reverseOrder())));
        return encontradas.size() > MAX_RESULTADOS ? encontradas.subList(0, MAX_RESULTADOS) : encontradas;
    }

    /** La venta más reciente del MISMO modelo (la mejor referencia de precio), si existe. */
    public Coincidencia ultimaVentaMismoModelo(List<Coincidencia> parecidos) {
        return parecidos.stream()
                .filter(c -> c.mismoModelo() && c.precioVenta() != null && c.fecha() != null)
                .max(Comparator.comparing(Coincidencia::fecha))
                .orElse(null);
    }
}
