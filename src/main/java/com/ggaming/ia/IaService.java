package com.ggaming.ia;

import com.ggaming.model.Coincidencia;
import com.ggaming.model.Producto;
import com.ggaming.model.Venta;
import com.ggaming.repository.ProductoRepository;
import com.ggaming.repository.VentaRepository;
import com.ggaming.service.HistorialService;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * SERVICIO que junta todo lo de IA para el admin:
 *  - Sugerencia de producto = historial propio (siempre) + IA (si está configurada).
 *  - Preguntas del admin con los datos de la tienda como contexto.
 *  - Un límite de pedidos por minuto para no gastar la cuota gratuita por error.
 */
@Service
public class IaService {

    private static final int MAX_PEDIDOS_POR_MINUTO = 15;
    private static final int MAX_VENTAS_EN_CONTEXTO = 300;

    private final AsistenteIa asistente;
    private final HistorialService historial;
    private final ProductoRepository productos;
    private final VentaRepository ventas;
    private final Deque<Instant> pedidosRecientes = new ArrayDeque<>();

    public IaService(AsistenteIa asistente, HistorialService historial,
                     ProductoRepository productos, VentaRepository ventas) {
        this.asistente = asistente;
        this.historial = historial;
        this.productos = productos;
        this.ventas = ventas;
    }

    /**
     * Lo que recibe el formulario de "nuevo producto".
     *
     * @param ultimaVenta la venta más reciente del MISMO modelo (null si nunca lo vendiste)
     * @param ia          null si la IA no está configurada o falló (ver iaError)
     */
    public record Sugerencia(
            List<Coincidencia> historial,
            Coincidencia ultimaVenta,
            boolean iaDisponible,
            SugerenciaIa ia,
            String iaError
    ) {}

    public boolean disponible() {
        return asistente.disponible();
    }

    public String nombreAsistente() {
        return asistente.nombre();
    }

    public Sugerencia sugerir(String nombre) {
        List<Coincidencia> parecidos = historial.buscarParecidos(nombre);
        Coincidencia ultima = historial.ultimaVentaMismoModelo(parecidos);
        if (!asistente.disponible()) {
            return new Sugerencia(parecidos, ultima, false, null, null);
        }
        try {
            controlarLimite();
            return new Sugerencia(parecidos, ultima, true, asistente.sugerirProducto(nombre, parecidos), null);
        } catch (IaException e) {
            // Si la IA falla, igual devolvemos el historial (que es lo más importante).
            return new Sugerencia(parecidos, ultima, true, null, e.getMessage());
        }
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
