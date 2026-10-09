package com.ggaming.bot;

import com.ggaming.model.ChatLinea;

import java.util.List;

/**
 * INTERFAZ del "cerebro" del bot (patrón Strategy, igual que AsistenteIa):
 * hoy lo implementa Gemini; mañana puede ser otra IA sin tocar el resto.
 */
public interface AgenteBot {

    /**
     * @param historial    mensajes anteriores de este cliente (del más viejo al más nuevo)
     * @param mensaje      lo que acaba de escribir
     * @param contexto     datos de la tienda + productos en stock, como texto
     */
    DecisionBot decidir(List<ChatLinea> historial, String mensaje, String contexto);
}
