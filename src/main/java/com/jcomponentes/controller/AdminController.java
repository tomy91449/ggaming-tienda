package com.jcomponentes.controller;

import com.jcomponentes.ia.IaService;
import com.jcomponentes.model.Categoria;
import com.jcomponentes.model.ProductoForm;
import com.jcomponentes.service.AdminService;
import com.jcomponentes.service.ImagenInvalidaException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * CONTROLADOR del panel /admin. Todas estas rutas están protegidas por
 * SecurityConfig: si no iniciaste sesión, te manda al login.
 *
 * Patrón "Post/Redirect/Get": después de guardar algo (POST) redirigimos a
 * una página (GET). Así, si recargás, no se vuelve a enviar el formulario.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService admin;
    private final IaService ia;

    public AdminController(AdminService admin, IaService ia) {
        this.admin = admin;
        this.ia = ia;
    }

    @GetMapping("/login")
    public String login() {
        return "admin/login";
    }

    /** Lista de todos los productos (disponibles y vendidos). */
    @GetMapping
    public String lista(Model model) {
        model.addAttribute("productos", admin.listarTodos());
        return "admin/lista";
    }

    // ------------------------------------------------------------ alta

    @GetMapping("/productos/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("form", new ProductoForm());
        return mostrarFormulario(model, null);
    }

    @PostMapping("/productos")
    public String crear(@Valid @ModelAttribute("form") ProductoForm form, BindingResult errores,
                        @RequestParam(name = "fotos", required = false) List<MultipartFile> fotos,
                        Model model, RedirectAttributes flash) {
        if (errores.hasErrors()) {
            return mostrarFormulario(model, null);
        }
        try {
            long id = admin.crear(form, fotos);
            flash.addFlashAttribute("mensaje", "Producto creado.");
            return "redirect:/admin/productos/" + id;
        } catch (ImagenInvalidaException e) {
            model.addAttribute("errorFotos", e.getMessage());
            return mostrarFormulario(model, null);
        }
    }

    // ------------------------------------------------------------ edición

    @GetMapping("/productos/{id}")
    public String editar(@PathVariable long id, Model model) {
        model.addAttribute("form", ProductoForm.desde(admin.obtener(id)));
        return mostrarFormulario(model, id);
    }

    @PostMapping("/productos/{id}")
    public String actualizar(@PathVariable long id,
                             @Valid @ModelAttribute("form") ProductoForm form, BindingResult errores,
                             @RequestParam(name = "fotos", required = false) List<MultipartFile> fotos,
                             Model model, RedirectAttributes flash) {
        if (errores.hasErrors()) {
            return mostrarFormulario(model, id);
        }
        try {
            admin.actualizar(id, form, fotos);
            flash.addFlashAttribute("mensaje", "Cambios guardados.");
            return "redirect:/admin/productos/" + id;
        } catch (ImagenInvalidaException e) {
            model.addAttribute("errorFotos", e.getMessage());
            return mostrarFormulario(model, id);
        }
    }

    // ------------------------------------------------------------ acciones rápidas

    /** Pantalla "¿A cuánto lo vendiste?" (alimenta la memoria de precios). */
    @GetMapping("/productos/{id}/vender")
    public String vender(@PathVariable long id, Model model) {
        model.addAttribute("producto", admin.obtener(id));
        return "admin/vender";
    }

    @PostMapping("/productos/{id}/vender")
    public String confirmarVenta(@PathVariable long id, @RequestParam long precioVenta, RedirectAttributes flash) {
        if (precioVenta < 0 || precioVenta > 10_000_000_000L) {
            flash.addFlashAttribute("error", "Precio de venta inválido.");
            return "redirect:/admin/productos/" + id + "/vender";
        }
        admin.vender(id, precioVenta);
        flash.addFlashAttribute("mensaje", "Venta registrada en el historial.");
        return "redirect:/admin";
    }

    @PostMapping("/productos/{id}/disponible")
    public String volverADisponible(@PathVariable long id, RedirectAttributes flash) {
        admin.volverADisponible(id);
        flash.addFlashAttribute("mensaje", "Marcado como disponible (y quitado del historial de ventas).");
        return "redirect:/admin";
    }

    /** Historial de ventas: la memoria de precios. */
    @GetMapping("/ventas")
    public String ventas(Model model) {
        var lista = admin.historialVentas();
        model.addAttribute("ventas", lista);
        model.addAttribute("totalVendido", lista.stream().mapToLong(v -> v.precioVenta()).sum());
        return "admin/ventas";
    }

    /** Chat con el asistente de IA. */
    @GetMapping("/asistente")
    public String asistente(Model model) {
        model.addAttribute("iaDisponible", ia.disponible());
        model.addAttribute("nombreIa", ia.nombreAsistente());
        return "admin/asistente";
    }

    @PostMapping("/productos/{id}/borrar")
    public String borrar(@PathVariable long id, RedirectAttributes flash) {
        admin.borrar(id);
        flash.addFlashAttribute("mensaje", "Producto borrado.");
        return "redirect:/admin";
    }

    @PostMapping("/fotos/{id}/principal")
    public String hacerPrincipal(@PathVariable long id, RedirectAttributes flash) {
        long productoId = admin.hacerPrincipal(id);
        flash.addFlashAttribute("mensaje", "Foto principal actualizada.");
        return "redirect:/admin/productos/" + productoId;
    }

    @PostMapping("/fotos/{id}/borrar")
    public String borrarFoto(@PathVariable long id, RedirectAttributes flash) {
        long productoId = admin.borrarFoto(id);
        flash.addFlashAttribute("mensaje", "Foto borrada.");
        return "redirect:/admin/productos/" + productoId;
    }

    // ------------------------------------------------------------ privados

    /** Carga lo que necesita el formulario. id == null significa "producto nuevo". */
    private String mostrarFormulario(Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("categorias", Categoria.values());
        model.addAttribute("maxFotos", AdminService.MAX_FOTOS);
        model.addAttribute("iaDisponible", ia.disponible());
        if (id != null) {
            model.addAttribute("producto", admin.obtener(id));
            model.addAttribute("fotos", admin.fotosDe(id));
        }
        return "admin/form";
    }
}
