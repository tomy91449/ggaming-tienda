package com.ggaming.bot;

/**
 * Los textos de los avisos que el bot te manda a tu WhatsApp personal.
 * La primera línea es exactamente el formato que pediste; la segunda agrega contexto.
 */
public final class Avisos {

    private Avisos() {}

    /** "El número: +549... tiene una consulta que no puedo responder" */
    public static String consulta(String telefono, String ultimoMensaje) {
        String texto = "El número: " + legible(telefono) + " tiene una consulta que no puedo responder";
        return ultimoMensaje == null || ultimoMensaje.isBlank() ? texto
                : texto + "\nÚltimo mensaje: «" + recortar(ultimoMensaje, 300) + "»";
    }

    /** "El número: +549... agendó un horario a las 18:30 hs" */
    public static String turno(String telefono, String hora, String dia, String productos) {
        String texto = "El número: " + legible(telefono) + " agendó un horario a las " + hora + " hs";
        String detalle = "Día: " + dia + (productos == null || productos.isBlank() ? "" : " · Productos: " + productos);
        return texto + "\n" + detalle;
    }

    /** 5493511234567 → +5493511234567 (los del simulador, "sim:549...", se marcan como prueba). */
    public static String legible(String telefono) {
        if (telefono.startsWith("sim:")) return "+" + telefono.substring(4) + " (prueba)";
        return telefono.startsWith("+") ? telefono : "+" + telefono;
    }

    private static String recortar(String s, int max) {
        String limpio = s.strip().replaceAll("\\s+", " ");
        return limpio.length() > max ? limpio.substring(0, max) + "…" : limpio;
    }
}
