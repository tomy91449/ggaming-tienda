package com.ggaming.ia;

import com.ggaming.model.Coincidencia;
import com.ggaming.model.Producto;
import com.ggaming.model.Venta;
import com.ggaming.repository.ProductoRepository;
import com.ggaming.repository.VentaRepository;
import com.ggaming.service.ClasificadorLocal;
import com.ggaming.service.HistorialService;
import com.ggaming.service.SimilitudService;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SERVICIO que junta todo lo de IA para el admin:
 *  - Sugerencia de producto = historial propio + ficha local (siempre) + IA (si está configurada).
 *  - Guarda las respuestas de la IA 12 horas: repetir el mismo nombre no gasta cuota.
 *  - Preguntas del admin con los datos de la tienda como contexto.
 *  - Un límite de pedidos por minuto para no gastar la cuota gratuita por error.
 */
@Service
public class IaService {

    private static final int MAX_PEDIDOS_POR_MINUTO = 15;
    private static final int MAX_VENTAS_EN_CONTEXTO = 300;
    private static final Duration DURACION_CACHE = Duration.ofHours(12);
    private static final int MAX_CACHE = 300;

    private final AsistenteIa asistente;
    private final HistorialService historial;
    private final ClasificadorLocal clasificador;
    private final SimilitudService similitud;
    private final ProductoRepository productos;
    private final VentaRepository ventas;
    private final Deque<Instant> pedidosRecientes = new ArrayDeque<>();
    private final Map<String, EntradaCache> cache = new ConcurrentHashMap<>();

    public IaService(AsistenteIa asistente, HistorialService historial, ClasificadorLocal clasificador,
                     SimilitudService similitud, ProductoRepository productos, VentaRepository ventas) {
        this.asistente = asistente;
        this.historial = historial;
        this.clasificador = clasificador;
        this.similitud = similitud;
        this.productos = productos;
        this.ventas = ventas;
    }

    /**
     * Lo que recibe el formulario de "nuevo producto".
     *
     * @param ultimaVenta la venta más reciente del MISMO modelo (null si nunca lo vendiste)
     * @param local       categoría + descripción armadas sin IA (siempre presentes)
     * @param ia          null si la IA no está configurada o falló (ver iaError)
     * @param desdeCache  true si la respuesta de la IA se reutilizó (no gastó cuota)
     */
    public record Sugerencia(
            List<Coincidencia> historial,
            Coincidencia ultimaVenta,
            ClasificadorLocal.Ficha local,
            boolean iaDisponible,
            SugerenciaIa ia,
            String iaError,
            boolean desdeCache
    ) {}

    private record EntradaCache(SugerenciaIa sugerencia, Instant momento) {}

    public boolean disponible() {
        return asistente.disponible();
    }

    public String nombreAsistente() {
        return asistente.nombre();
    }

    public Sugerencia sugerir(String nombre) {
        List<Coincidencia> parecidos = historial.buscarParecidos(nombre);
        Coincidencia ultima = historial.ultimaVentaMismoModelo(parecidos);
        // La ficha local (categoría + descripción) sale siempre, con o sin IA.
        ClasificadorLocal.Ficha local = clasificador.analizar(nombre);
        if (!asistente.disponible()) {
            return new Sugerencia(parecidos, ultima, local, false, null, null, false);
        }
        // Mismo nombre y mismo historial que hace poco: reutilizamos la respuesta (no gasta cuota).
        String clave = claveCache(nombre, parecidos);
        EntradaCache guardada = cache.get(clave);
        if (guardada != null && guardada.momento().plus(DURACION_CACHE).isAfter(Instant.now())) {
            return new Sugerencia(parecidos, ultima, local, true, guardada.sugerencia(), null, true);
        }
        try {
            controlarLimite();
            SugerenciaIa ia = asistente.sugerirProducto(nombre, parecidos);
            if (cache.size() >= MAX_CACHE) cache.clear();
            cache.put(clave, new EntradaCache(ia, Instant.now()));
            return new Sugerencia(parecidos, ultima, local, true, ia, null, false);
        } catch (IaException e) {
            // Si la IA falla, igual devolvemos el historial y la ficha local.
            return new Sugerencia(parecidos, ultima, local, true, null, e.getMessage(), false);
        }
    }

    /** El nombre normalizado + el historial: si vendés algo nuevo parecido, la clave cambia. */
    private String claveCache(String nombre, List<Coincidencia> parecidos) {
        StringBuilder sb = new StringBuilder(String.join(" ", similitud.normalizar(nombre)));
        parecidos.forEach(c -> sb.append('|').append(c.nombre()).append(c.precioPublicado())
                .append(c.precioVenta()).append(c.fecha()));
        return sb.toString();
    }

    public String preguntar(String pregunta, List<MensajeChat> conversacion) {
        if (!asistente.disponible()) {
            throw new IaException("La IA está desactivada: falta GEMINI_API_KEY en el archivo .env.");
        }
        controlarLimite();
        return asistente.responder(pregunta, conversacion, datosTienda());
    }

    /** Resumen de productos y ventas en texto, para que la IA responda sobre TU negocio. */
    String datosTienda() {
        NumberFormat pesos = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-AR"));
        StringBuilder sb = new StringBuilder("Productos en el catálogo (id | nombre | categoría | precio publicado | estado):\n");
        for (Producto p : productos.buscar(null, null)) {
            sb.append("- ").append(p.id()).append(" | ").append(p.nombre()).append(" | ")
              .append(p.categoria().getEtiqueta()).append(" | $").append(pesos.format(p.precio())).append(" | ")
              .append(p.vendido() ? "vendido" : "disponible").append('\n');
        }
        sb.append("\nVentas registradas, de la más nueva a la más vieja (fecha | nombre | categoría | publicado | vendido a):\n");
        List<Venta> lista = ventas.listar();
        lista.stream().limit(MAX_VENTAS_EN_CONTEXTO).forEach(v -> sb.append("- ").append(v.fecha()).append(" | ")
                .append(v.nombre()).append(" | ").append(v.categoria().getEtiqueta())
                .append(" | $").append(pesos.format(v.precioPublicado()))
                .append(" | $").append(pesos.format(v.precioVenta())).append('\n'));
        if (lista.isEmpty()) sb.append("(todavía no hay ventas registradas)\n");
        return sb.toString();
    }

    /** Máximo 15 pedidos por minuto (protege la cuota gratuita de clics repetidos o errores). */
    private synchronized void controlarLimite() {
        Instant haceUnMinuto = Instant.now().minusSeconds(60);
        while (!pedidosRecientes.isEmpty() && pedidosRecientes.peekFirst().isBefore(haceUnMinuto)) {
            pedidosRecientes.pollFirst();
        }
        if (pedidosRecientes.size() >= MAX_PEDIDOS_POR_MINUTO) {
            throw new IaException("Demasiadas consultas seguidas. Esperá un minuto.");
        }
        pedidosRecientes.addLast(Instant.now());
    }
}
