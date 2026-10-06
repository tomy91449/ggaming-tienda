package com.jcomponentes.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la comparación de nombres. Se corren con: mvnw test
 * Cada caso es un ejemplo real de lo que puede escribir el vendedor.
 */
class SimilitudServiceTest {

    private final SimilitudService similitud = new SimilitudService();

    @Test
    void normalizaAcentosUnidadesYSufijos() {
        assertThat(similitud.normalizar("Placa de Video NVIDIA GeForce RTX 3060 Ti 8 GB"))
                .containsExactly("nvidia", "geforce", "rtx", "3060ti", "8gb");
        assertThat(similitud.normalizar("Memoria RAM Kingston Fury 16GB DDR4"))
                .containsExactly("ram", "kingston", "fury", "16gb", "ddr4");
    }

    @Test
    void mismaPlacaDeOtraMarcaEsMismoModelo() {
        var r = similitud.comparar("NVIDIA RTX 3060 12GB", "Gigabyte RTX 3060 Eagle 12GB");
        assertThat(r.mismoModelo()).isTrue();
        assertThat(r.puntaje()).isGreaterThan(SimilitudService.UMBRAL_PARECIDO);
    }

    @Test
    void tiEsParecidaPeroNoElMismoModelo() {
        var r = similitud.comparar("RTX 3060 12GB", "ASUS RTX 3060 Ti Dual 8GB");
        assertThat(r.mismoModelo()).isFalse();
        assertThat(r.puntaje()).isGreaterThan(0.2);
    }

    @Test
    void tiConNombreLargoSigueSiendoParecida() {
        var r = similitud.comparar("MSI RTX 3060 Ventus 12GB", "ASUS RTX 3060 Ti Dual 8GB");
        assertThat(r.mismoModelo()).isFalse();
        assertThat(r.puntaje()).isGreaterThanOrEqualTo(SimilitudService.UMBRAL_PARECIDO);
    }

    @Test
    void misma3060ConOtraMemoriaNoEsElMismoModelo() {
        var r = similitud.comparar("RTX 3060 12GB", "RTX 3060 8GB");
        assertThat(r.mismoModelo()).isFalse();
    }

    @Test
    void ryzen5600NoEsIgualA5600x() {
        var r = similitud.comparar("AMD Ryzen 5 5600", "AMD Ryzen 5 5600X");
        assertThat(r.mismoModelo()).isFalse();
        assertThat(r.puntaje()).isGreaterThanOrEqualTo(SimilitudService.UMBRAL_PARECIDO);
    }

    @Test
    void productosDistintosNoSeParecen() {
        var r = similitud.comparar("Fuente Corsair CV650", "Kingston Fury 16GB DDR4");
        assertThat(r.mismoModelo()).isFalse();
        assertThat(r.puntaje()).isLessThan(SimilitudService.UMBRAL_PARECIDO);
    }

    @Test
    void fuenteDeOtraPotenciaEsParecidaNoIgual() {
        var r = similitud.comparar("Corsair CV650 650W", "Fuente Corsair CV550");
        assertThat(r.mismoModelo()).isFalse();
    }
}
