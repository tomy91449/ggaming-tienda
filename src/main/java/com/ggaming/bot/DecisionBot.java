package com.ggaming.bot;

import java.util.List;

/**
 * Lo que decide la IA ante cada mensaje del cliente.
 * La IA solo PROPONE: el código valida antes de agendar o avisar.
 *
 * @param respuesta texto para el cliente
 * @param accion    NINGUNA (solo responder), DERIVAR (avisar al dueño) o AGENDAR (turno)
 * @param dia       si AGENDAR: "2026-10-10"
 * @param hora      si AGENDAR: "18:30"
 * @param productos si AGENDAR: lo que viene a ver, ej: ["#1 NVIDIA RTX 3060 12GB"]
 * @param motivo    si DERIVAR: por qué (para registro)
 */
public record DecisionBot(String respuesta, String accion, String dia, String hora,
                          List<String> productos, String motivo) {

    public static final String NINGUNA = "NINGUNA";
    public static final String DERIVAR = "DERIVAR";
    public static final String AGENDAR = "AGENDAR";

    public String accionNormalizada() {
        return accion == null ? NINGUNA : accion.strip().toUpperCase();
    }
}
