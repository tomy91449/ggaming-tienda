package com.ggaming.service;

import com.ggaming.model.Categoria;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests de la ficha local (categoría + descripción sin IA). Se corren con: mvnw test */
class ClasificadorLocalTest {

    private final ClasificadorLocal clasificador = new ClasificadorLocal();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "MSI RTX 3060 Ventus 12GB, PLACA_VIDEO",
            "Sapphire Radeon RX 6600 8GB, PLACA_VIDEO",
            "Intel Arc A750 8GB, PLACA_VIDEO",
            "GTX 1660 Super, PLACA_VIDEO",
            "AMD Ryzen 5 5600X, PROCESADOR",
            "Intel Core i5-12400F, PROCESADOR",
            "Micro Intel i7 10700K, PROCESADOR",
            "Kingston Fury Beast 16GB DDR4 3200MHz, RAM",
            "Corsair Vengeance 2x16GB DDR5, RAM",
            "SSD Kingston NV2 1TB, DISCO",
            "WD Blue 2TB 7200 RPM, DISCO",
            "Samsung 980 M.2 NVMe 500GB, DISCO",
            "Corsair CV650 650W 80 Plus Bronze, FUENTE",
            "Fuente EVGA 600W, FUENTE",
            "Gigabyte B550M DS3H, MOTHERBOARD",
            "Mother Asus Prime B450M para Ryzen, MOTHERBOARD",
            "ASRock Z690 Phantom, MOTHERBOARD",
            "Gigabyte B550M Micro ATX, MOTHERBOARD",
            "Gabinete Sentey con 3 fans, GABINETE",
            "Mouse Logitech G203, MOUSE",
            "Logitech G502 25600 DPI, MOUSE",
            "Teclado Redragon Kumara mecánico RGB, TECLADO",
            "HyperX Alloy TKL switches red, TECLADO",
            "Monitor Samsung 24 pulgadas 144Hz, MONITOR",
            "LG UltraGear 27\" 165Hz, MONITOR",
            "Auriculares HyperX Cloud II 7.1, AURICULAR",
            "Headset Logitech G435 inalámbrico, AURICULAR",
            "Pad para mouse XL, MOUSE",
            "Webcam Logitech C920, OTRO"
    })
    void clasificaPorNombre(String nombre, Categoria esperada) {
        assertThat(clasificador.analizar(nombre).categoria()).isEqualTo(esperada);
    }

    @Test
    void descripcionSimpleSinMarcaConDatosDelNombre() {
        var ficha = clasificador.analizar("MSI RTX 3060 Ventus 12GB GDDR6");
        assertThat(ficha.descripcion()).startsWith("Placa de video").doesNotContainIgnoringCase("msi").doesNotContain("3060");
        assertThat(ficha.datos()).containsExactly("12 GB de memoria de video", "GDDR6");
        assertThat(ficha.descripcionCompleta()).contains("• 12 GB de memoria de video");
    }

    @Test
    void datosDeRamYFuente() {
        assertThat(clasificador.analizar("Kingston Fury 2x8GB DDR4 3200MHz CL16").datos())
                .containsExactly("Kit de 2 x 8 GB", "DDR4", "3200 MHz", "Latencia CL16");
        assertThat(clasificador.analizar("Fuente Corsair CV650 650W 80 Plus Bronze").datos())
                .containsExactly("650 W de potencia", "Certificación 80 Plus Bronze");
    }

    @Test
    void datosDePerifericos() {
        assertThat(clasificador.analizar("Monitor Samsung 24 pulgadas 144Hz").datos())
                .containsExactly("Pantalla de 24\"", "144 Hz");
        assertThat(clasificador.analizar("Mouse Logitech G502 inalámbrico 25600 DPI").datos())
                .containsExactly("Sensor de 25600 DPI", "Inalámbrico");
        assertThat(clasificador.analizar("Teclado Redragon mecánico RGB").descripcion())
                .startsWith("Teclado para PC").doesNotContainIgnoringCase("redragon");
    }

    @Test
    void todasLasCategoriasTienenDescripcion() {
        // Si alguien agrega una categoría nueva y se olvida la descripción, este test avisa.
        for (Categoria c : Categoria.values()) {
            assertThat(clasificador.descripcionDe(c)).as("descripción de " + c).isNotBlank();
        }
    }

    @Test
    void diferenciaSsdDeDiscoRigido() {
        assertThat(clasificador.analizar("SSD Kingston NV2 1TB NVMe").descripcion()).contains("SSD");
        assertThat(clasificador.analizar("Disco Seagate Barracuda 2TB 7200 RPM").descripcion()).contains("HDD");
    }
}
