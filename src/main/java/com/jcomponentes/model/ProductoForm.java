package com.jcomponentes.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Lo que llega del formulario de alta/edición en el admin.
 * Las anotaciones son las reglas de validación: Spring las revisa solo
 * y, si algo falla, volvemos a mostrar el formulario con el error al lado del campo.
 *
 * Es una clase con getters/setters (y no un record) porque Thymeleaf
 * la necesita así para llenar y leer los campos del formulario.
 */
public class ProductoForm {

    @NotBlank(message = "Poné un nombre.")
    @Size(max = 120, message = "Máximo 120 caracteres.")
    private String nombre;

    @Size(max = 2000, message = "Máximo 2000 caracteres.")
    private String descripcion;

    @NotNull(message = "Elegí una categoría.")
    private Categoria categoria;

    @NotNull(message = "Poné un precio.")
    @PositiveOrZero(message = "El precio no puede ser negativo.")
    @Max(value = 10_000_000_000L, message = "Precio demasiado alto.")
    private Long precio;

    private boolean vendido;

    /** Llena el formulario con un producto existente (para editarlo). */
    public static ProductoForm desde(Producto p) {
        ProductoForm f = new ProductoForm();
        f.nombre = p.nombre();
        f.descripcion = p.descripcion();
        f.categoria = p.categoria();
        f.precio = p.precio();
        f.vendido = p.vendido();
        return f;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre == null ? null : nombre.strip(); }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion == null ? null : descripcion.strip(); }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public Long getPrecio() { return precio; }
    public void setPrecio(Long precio) { this.precio = precio; }
    public boolean isVendido() { return vendido; }
    public void setVendido(boolean vendido) { this.vendido = vendido; }
}
