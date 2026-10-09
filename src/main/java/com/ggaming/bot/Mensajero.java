package com.ggaming.bot;

import java.util.List;

/** INTERFAZ para enviar mensajes de WhatsApp (en los tests se reemplaza por una falsa). */
public interface Mensajero {

    /** Mensaje de texto común (solo funciona si esa persona le escribió al bot en las últimas 24 hs). */
    void enviarTexto(String para, String texto);

    /** Plantilla aprobada por Meta (funciona siempre, aunque no haya escrito en 24 hs). */
    void enviarPlantilla(String para, String plantilla, List<String> parametros);
}
