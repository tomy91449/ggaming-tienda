package com.ggaming.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.ggaming.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

/**
 * Procesa las fotos que se suben desde el celular (lo que en Python haría Pillow):
 *  1. Verifica que sea una imagen de verdad (no un .exe renombrado a .jpg).
 *  2. La endereza según la orientación EXIF (sino salen giradas).
 *  3. La achica a 1600 px de lado máximo.
 *  4. La guarda como JPEG comprimido con nombre aleatorio.
 *
 * Re-codificar la imagen además BORRA los metadatos (ej: ubicación GPS de la foto)
 * y cualquier contenido escondido dentro del archivo.
 */
@Service
public class ImagenService {

    public static final String PREFIJO = "/fotos/";
    private static final int LADO_MAX = 1600;
    private static final long PIXELES_MAX = 60_000_000L; // evita "bombas" de imágenes enormes
    private static final float CALIDAD = 0.82f;

    private final Path carpeta;

    public ImagenService(AppProperties config) throws IOException {
        this.carpeta = Path.of(config.uploadsDir()).toAbsolutePath().normalize();
        Files.createDirectories(carpeta);
    }

    public Path carpeta() {
        return carpeta;
    }

    /** Procesa y guarda la imagen. Devuelve su ruta pública, ej: "/fotos/3f2a....jpg". */
    public String guardar(MultipartFile archivo) throws IOException {
        byte[] bytes = archivo.getBytes();
        BufferedImage imagen = leerSeguro(bytes);
        imagen = enderezar(imagen, orientacionExif(bytes));
        imagen = achicar(imagen);

        // Nombre aleatorio: nunca usamos el nombre que manda el usuario (evita pisar archivos o rutas raras).
        String nombre = UUID.randomUUID() + ".jpg";
        escribirJpeg(imagen, carpeta.resolve(nombre));
        return PREFIJO + nombre;
    }

    /** Borra el archivo de una foto. Solo dentro de la carpeta de fotos. */
    public void borrar(String ruta) {
        if (ruta == null || !ruta.startsWith(PREFIJO)) return;
        Path archivo = carpeta.resolve(ruta.substring(PREFIJO.length())).normalize();
        if (!archivo.startsWith(carpeta)) return; // protección contra "../../"
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ignorada) {
            // si no se pudo borrar, queda un archivo huérfano: no es grave
        }
    }

    // ---------------------------------------------------------------- pasos

    private BufferedImage leerSeguro(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(in);
            if (!lectores.hasNext()) {
                throw new ImagenInvalidaException("Uno de los archivos no es una imagen válida (usá JPG, PNG o WEBP).");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(in, true, true);
                // Miramos el tamaño ANTES de cargarla en memoria.
                long pixeles = (long) lector.getWidth(0) * lector.getHeight(0);
                if (pixeles > PIXELES_MAX) {
                    throw new ImagenInvalidaException("Una de las imágenes tiene una resolución demasiado grande.");
                }
                return lector.read(0);
            } finally {
                lector.dispose();
            }
        } catch (ImagenInvalidaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ImagenInvalidaException("No se pudo leer una de las imágenes (¿archivo dañado?).");
        }
    }

    /** Lee la etiqueta EXIF "Orientation" (1 = normal, 3 = 180°, 6 = 90°, 8 = 270°). */
    private int orientacionExif(byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception ignorada) {
            // sin EXIF (PNG, capturas de pantalla...): se deja como está
        }
        return 1;
    }

    private BufferedImage enderezar(BufferedImage img, int orientacion) {
        int grados = switch (orientacion) {
            case 3 -> 180;
            case 6 -> 90;
            case 8 -> 270;
            default -> 0;
        };
        if (grados == 0) return img;

        int w = img.getWidth(), h = img.getHeight();
        boolean intercambia = grados != 180; // a 90° y 270° el ancho y alto se invierten
        BufferedImage destino = lienzo(intercambia ? h : w, intercambia ? w : h);
        Graphics2D g = destino.createGraphics();
        g.translate(destino.getWidth() / 2.0, destino.getHeight() / 2.0);
        g.rotate(Math.toRadians(grados));
        g.translate(-w / 2.0, -h / 2.0);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return destino;
    }

    /** Achica en pasos de a la mitad (mejor calidad que de un saque) hasta 1600 px. */
    private BufferedImage achicar(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double escala = Math.min(1.0, (double) LADO_MAX / Math.max(w, h));
        int finalW = Math.max(1, (int) Math.round(w * escala));
        int finalH = Math.max(1, (int) Math.round(h * escala));

        // Si ya es chica, hace una sola pasada (igual sirve para convertirla a RGB).
        BufferedImage actual = img;
        do {
            w = Math.max(finalW, w / 2);
            h = Math.max(finalH, h / 2);
            BufferedImage paso = lienzo(w, h);
            Graphics2D g = paso.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(actual, 0, 0, w, h, null);
            g.dispose();
            actual = paso;
        } while (w != finalW || h != finalH);
        return actual;
    }

    /** Imagen RGB con fondo blanco (los PNG transparentes quedan sobre blanco, JPEG no tiene transparencia). */
    private BufferedImage lienzo(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private void escribirJpeg(BufferedImage img, Path destino) throws IOException {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = escritor.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(CALIDAD);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(destino.toFile())) {
            escritor.setOutput(out);
            escritor.write(null, new IIOImage(img, null, null), params);
        } finally {
            escritor.dispose();
        }
    }
}
