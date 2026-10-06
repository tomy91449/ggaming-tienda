package com.jcomponentes.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Compara nombres de productos de hardware y dice qué tan parecidos son.
 *
 * Idea: en hardware, lo que define el modelo son los "códigos" con números
 * (3060, 5600x, 16gb, 650w). Las marcas del fabricante de la placa (MSI, Gigabyte)
 * o palabras como "Ventus" importan menos. Ver el detalle del puntaje en comparar().
 */
@Service
public class SimilitudService {

    /** Desde este puntaje se muestra como "parecido". */
    public static final double UMBRAL_PARECIDO = 0.35;

    // Palabras que no ayudan a distinguir modelos.
    private static final Set<String> RELLENO = Set.of(
            "de", "la", "el", "los", "las", "con", "sin", "y", "para", "en",
            "placa", "video", "tarjeta", "grafica", "procesador", "memoria", "disco", "fuente",
            "usado", "usada", "nuevo", "nueva", "impecable", "caja", "oferta", "gaming");

    // Sufijos que cambian el modelo: "3060 ti" es OTRA placa que "3060".
    private static final Set<String> SUFIJOS = Set.of("ti", "super", "xt", "xtx", "x", "k", "f", "kf", "g", "gre");

    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");
    private static final Pattern NUMERO_SOLO = Pattern.compile("\\d+");
    private static final Pattern UNIDAD = Pattern.compile("gb|tb|mb|w|mhz|ghz|rpm");

    /** Resultado de comparar dos nombres. */
    public record Resultado(double puntaje, boolean mismoModelo) {}

    /**
     * Pasa un nombre a una lista de "tokens" comparables.
     * "NVIDIA GeForce RTX 3060 Ti 8 GB" -> [nvidia, geforce, rtx, 3060ti, 8gb]
     */
    public List<String> normalizar(String nombre) {
        if (nombre == null) return List.of();
        // Saca acentos: "Gráfica" -> "grafica"
        String texto = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String[] partes = NO_ALFANUMERICO.matcher(texto).replaceAll(" ").trim().split("\\s+");

        List<String> tokens = new ArrayList<>();
        for (String parte : partes) {
            if (parte.isEmpty() || RELLENO.contains(parte)) continue;
            int ultimo = tokens.size() - 1;
            boolean anteriorEsNumero = ultimo >= 0 && tieneDigitos(tokens.get(ultimo));
            // "8 gb" -> "8gb"   y   "3060 ti" -> "3060ti"
            if (anteriorEsNumero && NUMERO_SOLO.matcher(tokens.get(ultimo)).matches()
                    && (UNIDAD.matcher(parte).matches() || SUFIJOS.contains(parte))) {
                tokens.set(ultimo, tokens.get(ultimo) + parte);
            } else {
                tokens.add(parte);
            }
        }
        return tokens;
    }

    /**
     * Puntaje final (0 a 1):
     *   30% palabras en común
     *   50% códigos de modelo con números (3060, 5600x, b550m, cv650)
     *   20% capacidades (12gb, 650w, 3200mhz)
     */
    public Resultado comparar(String nombreA, String nombreB) {
        Set<String> a = new LinkedHashSet<>(normalizar(nombreA));
        Set<String> b = new LinkedHashSet<>(normalizar(nombreB));
        if (a.isEmpty() || b.isEmpty()) return new Resultado(0, false);

        double palabras = solapamiento(a, b);

        Set<String> modelosA = codigos(a, false), modelosB = codigos(b, false);
        double modelo;
        if (modelosA.isEmpty() && modelosB.isEmpty()) {
            modelo = palabras; // sin números (ej: "Gabinete Sentey"): solo cuentan las palabras
        } else if (modelosA.isEmpty() || modelosB.isEmpty()) {
            modelo = 0;
        } else {
            modelo = parecidoCodigos(modelosA, modelosB);
        }

        Set<String> unidadesA = codigos(a, true), unidadesB = codigos(b, true);
        // Si uno no dice la capacidad, no sabemos: valor neutro.
        double unidades = (unidadesA.isEmpty() || unidadesB.isEmpty()) ? 0.5 : parecidoCodigos(unidadesA, unidadesB);

        double puntaje = 0.3 * palabras + 0.5 * modelo + 0.2 * unidades;
        // Mismo modelo: todos los códigos de modelo coinciden y las capacidades no se contradicen
        // (una RTX 3060 de 8GB no es la misma que una de 12GB).
        boolean mismoModelo = !modelosA.isEmpty() && modelo >= 0.99 && unidades >= 0.5;
        return new Resultado(Math.round(puntaje * 100) / 100.0, mismoModelo);
    }

    // ---------------------------------------------------------------- ayudas

    private static boolean tieneDigitos(String s) {
        return s.chars().anyMatch(Character::isDigit);
    }

    /** Tokens con números: de modelo ("3060ti", "ddr4") o capacidades ("12gb", "650w"). */
    private static Set<String> codigos(Set<String> tokens, boolean capacidades) {
        Set<String> res = new LinkedHashSet<>();
        for (String t : tokens) {
            if (!tieneDigitos(t)) continue;
            boolean esCapacidad = !t.matches("\\d+") && UNIDAD.matcher(t.replaceAll("^\\d+", "")).matches();
            if (esCapacidad == capacidades) res.add(t);
        }
        return res;
    }

    /**
     * Palabras en común sobre el nombre más corto: "RTX 3060" vs "ASUS RTX 3060 Dual"
     * comparten todo lo del corto, aunque el largo tenga palabras de más.
     */
    private static double solapamiento(Set<String> a, Set<String> b) {
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        return (double) inter.size() / Math.min(a.size(), b.size());
    }

    /**
     * Para cada código de A busca el mejor de B: idéntico = 1,
     * uno empieza con el otro ("3060" vs "3060ti") = 0.5 (familia parecida),
     * misma unidad con otra cantidad ("12gb" vs "8gb") = 0.3, si no = 0.
     */
    private static double parecidoCodigos(Set<String> a, Set<String> b) {
        Set<String> todos = new HashSet<>(a);
        todos.addAll(b);
        double suma = 0;
        for (String c : todos) {
            Set<String> otro = a.contains(c) ? b : a;
            double mejor = 0;
            for (String o : otro) {
                if (o.equals(c)) { mejor = 1; break; }
                if (o.startsWith(c) || c.startsWith(o)) mejor = Math.max(mejor, 0.5);
                if (o.replaceAll("^\\d+", "").equals(c.replaceAll("^\\d+", "")) && !o.matches("\\d+")) {
                    mejor = Math.max(mejor, 0.3);
                }
            }
            suma += mejor;
        }
        return suma / todos.size();
    }
}
