package com.ggaming.ia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggaming.model.Categoria;
import com.ggaming.model.Coincidencia;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementación de AsistenteIa con Gemini. Acá están los PROMPTS:
 * las instrucciones que le damos al modelo para cada tarea.
 */
@Service
public class GeminiAsistente implements AsistenteIa {

    private static final int MAX_MENSAJES_PREVIOS = 10;
    private static final int MAX_LARGO_MENSAJE = 2000;

    private final GeminiCliente cliente;
    private final IaProperties config;
    private final ObjectMapper json;

    public GeminiAsistente(GeminiCliente cliente, IaProperties config, ObjectMapper json) {
        this.cliente = cliente;
        this.config = config;
        this.json = json;
    }

    @Override
    public boolean disponible() {
        return config.configurada();
    }

    @Override
    public String nombre() {
        return "Gemini (" + config.modelo() + ")";
    }

    // ===================================================== sugerir producto

    @Override
    public SugerenciaIa sugerirProducto(String nombreProducto, List<Coincidencia> historial) {
        String categorias = Arrays.stream(Categoria.values()).map(Enum::name).collect(Collectors.joining(", "));
        String sistema = """
                Sos el asistente de GGaming, una tienda de Argentina que vende hardware de PC \
                (mayormente usado) por WhatsApp. Hoy es %s.
                El dueño te pasa el NOMBRE de un producto que quiere publicar y su HISTORIAL propio \
                de productos iguales o parecidos (ventas reales y publicaciones).

                Tu tarea:
                1. categoria: exactamente una de [%s].
                2. marca y modelo exactos.
                3. specs: 3 a 6 especificaciones técnicas clave y cortas (ej: "12 GB GDDR6", "TDP 170 W"), \
                sin marca ni nombre de modelo.
                4. descripcion: 1 o 2 oraciones SIMPLES en español rioplatense que expliquen qué es el \
                componente y para qué sirve (ej: "Placa de video para PC de escritorio, ideal para jugar en 1080p."). \
                NO nombres marca, fabricante ni modelo. NO inventes estado, uso, garantía, caja ni accesorios: \
                eso lo completa el dueño.
                5. precio en pesos argentinos (ARS), números enteros sin puntos ni símbolos:
                   - Si el historial tiene ventas del MISMO modelo, son la referencia principal. \
                Si son de hace meses, ajustalas por la inflación de Argentina y decilo.
                   - Los productos parecidos sirven como referencia relativa (más o menos potente).
                   %s
                   - Dá precioRecomendado y un rango precioMin / precioMax.
                6. explicacion: 1 a 3 oraciones de por qué ese precio, nombrando las referencias usadas.
                Si el nombre es ambiguo o no es hardware de PC, explicalo en "explicacion" y poné precios en 0.

                Respondé SOLO con un objeto JSON, sin texto antes ni después, con estas claves:
                {"categoria": "...", "marca": "...", "modelo": "...", "specs": ["..."], \
                "descripcion": "...", "precioRecomendado": 0, "precioMin": 0, "precioMax": 0, "explicacion": "..."}
                """.formatted(LocalDate.now(), categorias,
                config.busquedaWeb()
                        ? "- Buscá en Google el precio actual de ese modelo USADO en Argentina (MercadoLibre, etc.) para validar."
                        : "- No tenés acceso a internet: basate en el historial y en tu conocimiento "
                          + "de precios de hardware usado en Argentina.");

        String pedido = "Producto: " + nombreProducto + "\n\nHistorial propio (JSON):\n" + historialComoJson(historial);

        GeminiCliente.Respuesta r = cliente.generar(sistema, List.of(mensaje("user", pedido)), true, config.busquedaWeb());
        return limpiar(leerJson(r.texto())).conOrigen(r.fuentes(), r.modelo());
    }

    private String historialComoJson(List<Coincidencia> historial) {
        if (historial.isEmpty()) return "[] (no hay nada parecido en el historial)";
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Coincidencia c : historial) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("nombre", c.nombre());
            f.put("relacion", c.mismoModelo() ? "MISMO MODELO" : "parecido");
            f.put("estado", c.origen());
            f.put("precioPublicado", c.precioPublicado());
            if (c.precioVenta() != null) f.put("precioVenta", c.precioVenta());
            if (c.fecha() != null) f.put("fechaVenta", c.fecha().toString());
            filas.add(f);
        }
        try {
            return json.writeValueAsString(filas);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /** Extrae el objeto JSON aunque venga envuelto en ```json ... ``` o con texto alrededor. */
    private SugerenciaIa leerJson(String texto) {
        int inicio = texto.indexOf('{');
        int fin = texto.lastIndexOf('}');
        if (inicio < 0 || fin <= inicio) {
            throw new IaException("La IA no respondió en el formato esperado. Probá de nuevo.");
        }
        try {
            return json.readValue(texto.substring(inicio, fin + 1), SugerenciaIa.class);
        } catch (JsonProcessingException e) {
            throw new IaException("La IA no respondió en el formato esperado. Probá de nuevo.");
        }
    }

    /** Nunca confiamos ciegamente en lo que devuelve un modelo: validamos y recortamos. */
    private SugerenciaIa limpiar(SugerenciaIa s) {
        String categoria = Arrays.stream(Categoria.values()).map(Enum::name)
                .filter(n -> n.equals(s.categoria())).findFirst().orElse(null);
        List<String> specs = s.specs() == null ? List.of()
                : s.specs().stream().filter(x -> x != null && !x.isBlank()).limit(8).map(x -> cortar(x, 80)).toList();
        return new SugerenciaIa(categoria, cortar(s.marca(), 60), cortar(s.modelo(), 80),
                cortar(s.descripcion(), 1500), specs,
                positivo(s.precioRecomendado()), positivo(s.precioMin()), positivo(s.precioMax()),
                cortar(s.explicacion(), 800), List.of(), null);
    }

    // ===================================================== preguntas del admin

    @Override
    public String responder(String pregunta, List<MensajeChat> conversacion, String datosTienda) {
        String sistema = """
                Sos el asistente del dueño de GGaming, una tienda de Argentina que vende \
                hardware de PC por WhatsApp. Hoy es %s.
                Respondés preguntas del dueño sobre su stock, sus ventas y sus precios, y también \
                dudas técnicas de hardware (compatibilidad, qué conviene, etc.).
                Reglas:
                - Para preguntas sobre el negocio, la ÚNICA fuente de verdad son los DATOS DE LA TIENDA \
                de abajo. No inventes productos ni ventas; si algo no está en los datos, decilo.
                - Español rioplatense, claro y breve.
                - Texto plano: sin markdown, sin asteriscos ni numerales. Para listas usá guiones.
                - Montos en pesos con punto de miles (ej: $350.000).

                DATOS DE LA TIENDA:
                %s
                """.formatted(LocalDate.now(), datosTienda);

        List<Map<String, Object>> contenidos = new ArrayList<>();
        List<MensajeChat> previos = conversacion == null ? List.of() : conversacion;
        int desde = Math.max(0, previos.size() - MAX_MENSAJES_PREVIOS);
        for (MensajeChat m : previos.subList(desde, previos.size())) {
            if (m == null || m.texto() == null || m.texto().isBlank()) continue;
            contenidos.add(mensaje("asistente".equals(m.rol()) ? "model" : "user", cortar(m.texto(), MAX_LARGO_MENSAJE)));
        }
        contenidos.add(mensaje("user", cortar(pregunta, MAX_LARGO_MENSAJE)));
        return cliente.generar(sistema, contenidos, false, config.busquedaWeb()).texto().strip();
    }

    // ===================================================== ayudas

    private static Map<String, Object> mensaje(String rol, String texto) {
        return Map.of("role", rol, "parts", List.of(Map.of("text", texto)));
    }

    private static String cortar(String s, int max) {
        if (s == null) return null;
        String limpio = s.strip();
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }

    private static Long positivo(Long n) {
        return n == null || n < 0 ? null : n;
    }
}
