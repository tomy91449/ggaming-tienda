package com.ggaming.service;

import com.ggaming.model.Categoria;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FICHA LOCAL (sin IA): a partir del nombre, con reglas y expresiones regulares,
 * deduce la categoría, una descripción simple de qué es el componente
 * (sin marca ni fabricante) y los datos técnicos que aparecen en el nombre.
 *
 * Es instantáneo, gratis y siempre funciona: si la IA está apagada o sin cuota,
 * el formulario igual se completa con esto.
 */
@Service
public class ClasificadorLocal {

    /** @param datos ej: ["12 GB de memoria", "GDDR6"] (sin marca ni modelo) */
    public record Ficha(Categoria categoria, String descripcion, List<String> datos) {

        /** Descripción lista para el formulario: el texto + los datos como viñetas. */
        public String descripcionCompleta() {
            if (datos.isEmpty()) return descripcion;
            StringBuilder sb = new StringBuilder(descripcion).append("\n");
            datos.forEach(d -> sb.append("\n• ").append(d));
            return sb.toString();
        }
    }

    // ---- Paso 1: si el nombre DICE la categoría ("Fuente ...", "Mother ..."), manda eso.
    private static final Map<Categoria, Pattern> PALABRAS = new LinkedHashMap<>();
    // ---- Paso 2: si no, se reconoce por los modelos típicos (rtx, ryzen, ddr4, b550...).
    private static final Map<Categoria, Pattern> MODELOS = new LinkedHashMap<>();

    static {
        PALABRAS.put(Categoria.FUENTE, p("\\b(fuente|psu)\\b"));
        PALABRAS.put(Categoria.GABINETE, p("\\b(gabinete|torre|mid ?tower|full ?tower)\\b"));
        PALABRAS.put(Categoria.MOTHERBOARD, p("\\b(mother|motherboard|placa madre|placa base|mainboard)\\b"));
        PALABRAS.put(Categoria.PLACA_VIDEO, p("\\b(placa de video|tarjeta (de video|grafica)|gpu)\\b"));
        PALABRAS.put(Categoria.RAM, p("\\b(memoria|ram)\\b"));
        PALABRAS.put(Categoria.DISCO, p("\\b(disco|rigido|ssd|hdd|nvme)\\b"));
        // "micro" = procesador, pero no en "Micro ATX" (formato de motherboard/gabinete)
        PALABRAS.put(Categoria.PROCESADOR, p("\\b(procesador|cpu|micro(?! ?atx))\\b"));

        // El orden importa: "Intel Arc A750" es placa de video aunque diga Intel.
        MODELOS.put(Categoria.PLACA_VIDEO, p("\\b(rtx|gtx|geforce|radeon|quadro|rx ?\\d{3,4}|gt ?\\d{3,4}|arc ?[ab]\\d{3})\\b"));
        MODELOS.put(Categoria.MOTHERBOARD, p("\\b([abhxz]\\d{3}[a-z]?m?)\\b"));
        MODELOS.put(Categoria.PROCESADOR, p("\\b(ryzen|athlon|threadripper|core ?i[3579]|i[3579][ -]?\\d{4,5}[a-z]*|core ultra|pentium|celeron|xeon|intel)\\b"));
        MODELOS.put(Categoria.RAM, p("\\b(ddr[2-5]|sodimm|dimm)\\b"));
        MODELOS.put(Categoria.DISCO, p("\\b(m\\.?2|sata|barracuda|\\d+ ?tb)\\b"));
        MODELOS.put(Categoria.FUENTE, p("\\b(80 ?plus|\\d{3,4} ?w)\\b"));
        MODELOS.put(Categoria.GABINETE, p("\\b(case)\\b"));
    }

    private static final Map<Categoria, String> DESCRIPCIONES = Map.of(
            Categoria.PLACA_VIDEO, "Placa de video para PC de escritorio. Procesa los gráficos: juegos, edición de video y diseño.",
            Categoria.PROCESADOR, "Procesador (CPU) para PC de escritorio. Es el cerebro de la computadora: ejecuta los programas y los juegos.",
            Categoria.RAM, "Memoria RAM para PC. Permite tener más programas y pestañas abiertos a la vez y que todo funcione más fluido.",
            Categoria.FUENTE, "Fuente de alimentación para PC. Le da energía estable y segura a todos los componentes.",
            Categoria.MOTHERBOARD, "Motherboard (placa madre) para PC. Conecta todos los componentes: procesador, memoria, placa de video y discos.",
            Categoria.GABINETE, "Gabinete para PC. Aloja y protege los componentes y ayuda a mantenerlos bien ventilados.",
            Categoria.OTRO, "Componente o accesorio para PC.");

    public Ficha analizar(String nombre) {
        String t = normalizar(nombre);
        Categoria categoria = clasificar(t);
        return new Ficha(categoria, describir(categoria, t), datos(categoria, t));
    }

    // ---------------------------------------------------------------- pasos

    Categoria clasificar(String t) {
        for (var e : PALABRAS.entrySet()) if (e.getValue().matcher(t).find()) return e.getKey();
        for (var e : MODELOS.entrySet()) if (e.getValue().matcher(t).find()) return e.getKey();
        return Categoria.OTRO;
    }

    private String describir(Categoria c, String t) {
        if (c != Categoria.DISCO) return DESCRIPCIONES.get(c);
        if (t.matches(".*\\b(ssd|nvme|m\\.?2)\\b.*")) {
            return "Unidad de estado sólido (SSD) para PC. Guarda el sistema, los programas y los archivos, con arranque y cargas muy rápidos.";
        }
        if (t.matches(".*\\b(hdd|rigido|barracuda|rpm)\\b.*") || t.matches(".*\\b\\d+ ?rpm\\b.*")) {
            return "Disco rígido (HDD) para PC. Ideal para guardar mucha información: fotos, videos, juegos y backups.";
        }
        return "Unidad de almacenamiento para PC. Guarda el sistema, los programas y los archivos.";
    }

    /** Datos técnicos que aparecen en el nombre (nunca la marca ni el modelo). */
    private List<String> datos(Categoria c, String t) {
        Set<String> d = new LinkedHashSet<>();

        Matcher kit = p("\\b(\\d) ?x ?(\\d+) ?gb\\b").matcher(t);
        if (kit.find()) d.add("Kit de " + kit.group(1) + " x " + kit.group(2) + " GB");

        Matcher cap = p("\\b(\\d+(?:[.,]\\d+)?) ?(gb|tb)\\b").matcher(t);
        if (!kit.find(0) && cap.find()) {
            String valor = cap.group(1) + " " + cap.group(2).toUpperCase(Locale.ROOT);
            d.add(switch (c) {
                case PLACA_VIDEO -> valor + " de memoria de video";
                case RAM -> valor + " de capacidad";
                case DISCO -> valor + " de almacenamiento";
                default -> valor;
            });
        }
        agregar(d, t, "\\bg?ddr([2-7])x?\\b", m -> (m.group().startsWith("g") ? "GDDR" : "DDR") + m.group(1));
        agregar(d, t, "\\b(\\d{4}) ?mhz\\b", m -> m.group(1) + " MHz");
        agregar(d, t, "\\bcl ?(\\d{2})\\b", m -> "Latencia CL" + m.group(1));
        agregar(d, t, "\\b(\\d{3,4}) ?w\\b", m -> m.group(1) + " W de potencia");
        agregar(d, t, "\\b80 ?plus(?: (white|bronze|silver|gold|platinum|titanium))?\\b",
                m -> "Certificación 80 Plus" + (m.group(1) == null ? "" : " " + capitalizar(m.group(1))));
        agregar(d, t, "\\b(semi ?-?modular|modular)\\b", m -> m.group(1).startsWith("semi") ? "Semi-modular" : "Modular");
        agregar(d, t, "\\bnvme\\b", m -> "NVMe");
        agregar(d, t, "\\bm\\.?2\\b", m -> "Formato M.2");
        agregar(d, t, "\\bpcie ?(\\d(?:\\.\\d)?)\\b", m -> "PCIe " + m.group(1));
        agregar(d, t, "\\bsata\\b", m -> "Conexión SATA");
        agregar(d, t, "\\b(\\d{4,5}) ?rpm\\b", m -> m.group(1) + " RPM");
        agregar(d, t, "\\b(am[45]|lga ?\\d{4})\\b", m -> "Socket " + m.group(1).toUpperCase(Locale.ROOT).replace(" ", ""));
        agregar(d, t, "\\b(micro ?atx|matx|mini ?itx|itx|e-?atx|atx)\\b", m -> "Formato " + formato(m.group(1)));
        agregar(d, t, "\\b(\\d+) ?(fans|ventiladores|coolers)\\b", m -> m.group(1) + " ventiladores");
        agregar(d, t, "\\bvidrio( templado)?\\b", m -> "Lateral de vidrio templado");
        agregar(d, t, "\\b(rgb|argb)\\b", m -> "Iluminación " + m.group(1).toUpperCase(Locale.ROOT));
        agregar(d, t, "\\bcooler (stock|incluido)|con cooler\\b", m -> "Incluye cooler");
        return new ArrayList<>(d);
    }

    // ---------------------------------------------------------------- ayudas

    private static Pattern p(String regex) {
        return Pattern.compile(regex);
    }

    private static void agregar(Set<String> d, String t, String regex, java.util.function.Function<Matcher, String> texto) {
        Matcher m = p(regex).matcher(t);
        if (m.find()) d.add(texto.apply(m));
    }

    /** Minúsculas y sin acentos: "Gráfica" -> "grafica". */
    private static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String formato(String f) {
        String limpio = f.replace(" ", "").replace("-", "");
        return switch (limpio) {
            case "microatx", "matx" -> "Micro ATX";
            case "miniitx", "itx" -> "Mini ITX";
            case "eatx" -> "E-ATX";
            default -> "ATX";
        };
    }
}
