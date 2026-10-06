package com.jcomponentes.service;

import com.jcomponentes.model.Foto;
import com.jcomponentes.model.Producto;
import com.jcomponentes.model.ProductoForm;
import com.jcomponentes.model.Venta;
import com.jcomponentes.repository.FotoRepository;
import com.jcomponentes.repository.ProductoRepository;
import com.jcomponentes.repository.VentaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * SERVICIO del panel admin: alta, edición, vendido, borrado y fotos.
 * Coordina dos repositorios (productos y fotos) y el procesamiento de imágenes.
 *
 * @Transactional: si algo falla a mitad de camino, los cambios en la base se deshacen.
 */
@Service
public class AdminService {

    public static final int MAX_FOTOS = 10;

    private final ProductoRepository productos;
    private final FotoRepository fotos;
    private final VentaRepository ventas;
    private final ImagenService imagenes;

    public AdminService(ProductoRepository productos, FotoRepository fotos,
                        VentaRepository ventas, ImagenService imagenes) {
        this.productos = productos;
        this.fotos = fotos;
        this.ventas = ventas;
        this.imagenes = imagenes;
    }

    public List<Producto> listarTodos() {
        return productos.buscar(null, null);
    }

    public Producto obtener(long id) {
        return productos.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<Foto> fotosDe(long productoId) {
        return fotos.listarPorProducto(productoId);
    }

    @Transactional
    public long crear(ProductoForm form, List<MultipartFile> archivos) {
        // Primero procesamos las fotos: si alguna es inválida, no se crea nada.
        List<String> rutas = procesarFotos(archivos, 0);
        long id = productos.insertar(form);
        agregarFotos(id, rutas, 0);
        if (form.isVendido()) {
            registrarVenta(obtener(id), form.getPrecio());
        }
        return id;
    }

    @Transactional
    public void actualizar(long id, ProductoForm form, List<MultipartFile> archivos) {
        Producto antes = obtener(id); // 404 si no existe
        int yaTiene = fotos.contar(id);
        List<String> rutas = procesarFotos(archivos, yaTiene);
        productos.actualizar(id, form);
        agregarFotos(id, rutas, yaTiene);
        // Si con el formulario cambió el estado, el historial acompaña.
        if (!antes.vendido() && form.isVendido()) {
            registrarVenta(obtener(id), form.getPrecio());
        } else if (antes.vendido() && !form.isVendido()) {
            ventas.borrarPorProducto(id);
        }
    }

    /** Marca como vendido y guarda en el historial a cuánto se vendió realmente. */
    @Transactional
    public void vender(long id, long precioVenta) {
        Producto p = obtener(id);
        if (p.vendido()) return;
        productos.cambiarVendido(id, true);
        registrarVenta(p, precioVenta);
    }

    /** Vuelve a "disponible" (por si se marcó vendido por error): también se quita del historial. */
    @Transactional
    public void volverADisponible(long id) {
        obtener(id);
        productos.cambiarVendido(id, false);
        ventas.borrarPorProducto(id);
    }

    public List<Venta> historialVentas() {
        return ventas.listar();
    }

    private void registrarVenta(Producto p, long precioVenta) {
        ventas.insertar(p.id(), p.nombre(), p.categoria(), p.precio(), precioVenta, LocalDate.now());
    }

    /** Borra el producto y sus fotos. Sus ventas quedan en el historial (memoria de precios). */
    @Transactional
    public void borrar(long id) {
        obtener(id);
        List<Foto> suyas = fotos.listarPorProducto(id);
        fotos.borrarPorProducto(id);
        productos.borrar(id);
        suyas.forEach(f -> imagenes.borrar(f.ruta()));
    }

    /** Elige qué foto se ve en el catálogo. Devuelve el id del producto. */
    @Transactional
    public long hacerPrincipal(long fotoId) {
        Foto foto = fotos.buscarPorId(fotoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        productos.ponerFotoPrincipal(foto.productoId(), foto.ruta());
        return foto.productoId();
    }

    /** Borra una foto. Si era la principal, pasa a serlo la siguiente. Devuelve el id del producto. */
    @Transactional
    public long borrarFoto(long fotoId) {
        Foto foto = fotos.buscarPorId(fotoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        long productoId = foto.productoId();
        fotos.borrar(fotoId);
        if (foto.ruta().equals(obtener(productoId).foto())) {
            String siguiente = fotos.listarPorProducto(productoId).stream()
                    .map(Foto::ruta).findFirst().orElse(null);
            productos.ponerFotoPrincipal(productoId, siguiente);
        }
        imagenes.borrar(foto.ruta());
        return productoId;
    }

    // ------------------------------------------------------------- privados

    /** Valida y guarda las fotos en disco. Si una falla, borra las que ya se guardaron. */
    private List<String> procesarFotos(List<MultipartFile> archivos, int yaTiene) {
        List<MultipartFile> validos = archivos == null ? List.of()
                : archivos.stream().filter(a -> a != null && !a.isEmpty()).toList();
        if (yaTiene + validos.size() > MAX_FOTOS) {
            throw new ImagenInvalidaException("Máximo " + MAX_FOTOS + " fotos por producto (ya tiene " + yaTiene + ").");
        }
        List<String> rutas = new ArrayList<>();
        try {
            for (MultipartFile archivo : validos) {
                rutas.add(imagenes.guardar(archivo));
            }
        } catch (IOException | RuntimeException e) {
            rutas.forEach(imagenes::borrar);
            if (e instanceof RuntimeException re) throw re;
            throw new UncheckedIOException((IOException) e);
        }
        return rutas;
    }

    private void agregarFotos(long productoId, List<String> rutas, int ordenInicial) {
        for (int i = 0; i < rutas.size(); i++) {
            fotos.insertar(productoId, rutas.get(i), ordenInicial + i);
        }
        // Si el producto no tenía foto principal, usamos la primera nueva.
        if (!rutas.isEmpty() && obtener(productoId).foto() == null) {
            productos.ponerFotoPrincipal(productoId, rutas.get(0));
        }
    }
}
