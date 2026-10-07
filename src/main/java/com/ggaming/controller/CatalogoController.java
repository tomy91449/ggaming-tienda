package com.ggaming.controller;

import com.ggaming.config.AppProperties;
import com.ggaming.model.Categoria;
import com.ggaming.service.CatalogoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * CONTROLADOR: recibe la petición HTTP, le pide los datos al servicio,
 * los pone en el "Model" y elige qué plantilla HTML renderizar.
 * No tiene SQL ni reglas de negocio.
 */
@Controller
public class CatalogoController {

    private final CatalogoService catalogo;
    private final AppProperties config;

    public CatalogoController(CatalogoService catalogo, AppProperties config) {
        this.catalogo = catalogo;
        this.config = config;
    }

    /** Página pública: GET /?q=rtx&categoria=PLACA_VIDEO */
    @GetMapping("/")
    public String catalogo(@RequestParam(required = false) String q,
                           @RequestParam(required = false) Categoria categoria,
                           Model model) {
        model.addAttribute("productos", catalogo.buscar(q, categoria));
        model.addAttribute("categorias", Categoria.values());
        // Devolvemos los filtros para que el formulario los recuerde.
        model.addAttribute("q", catalogo.normalizarBusqueda(q));
        model.addAttribute("categoriaActual", categoria);
        // El número sale de la configuración (variable WHATSAPP_NUMBER), no del HTML.
        model.addAttribute("whatsapp", config.whatsappNumber());
        return "catalogo"; // -> templates/catalogo.html
    }
}
