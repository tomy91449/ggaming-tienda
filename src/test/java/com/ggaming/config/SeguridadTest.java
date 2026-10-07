package com.ggaming.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests de las protecciones del login. Se corren con: mvnw test */
class SeguridadTest {

    @Test
    void cincoFallosBloqueanEsaIp() {
        IntentosLogin intentos = new IntentosLogin();
        for (int i = 0; i < IntentosLogin.MAX_FALLOS; i++) intentos.fallo("1.1.1.1");
        assertThat(intentos.bloqueada("1.1.1.1")).isTrue();
        assertThat(intentos.bloqueada("2.2.2.2")).isFalse();
    }

    @Test
    void cambiarDeIpEnCadaIntentoNoEvitaElBloqueoGlobal() {
        IntentosLogin intentos = new IntentosLogin();
        for (int i = 0; i < IntentosLogin.MAX_FALLOS_GLOBALES; i++) intentos.fallo("10.0.0." + i);
        assertThat(intentos.bloqueada("203.0.113.99")).isTrue();
    }

    @Test
    void unLoginCorrectoPerdonaLosFallosDeEsaIp() {
        IntentosLogin intentos = new IntentosLogin();
        for (int i = 0; i < IntentosLogin.MAX_FALLOS - 1; i++) intentos.fallo("1.1.1.1");
        intentos.exito("1.1.1.1");
        intentos.fallo("1.1.1.1");
        assertThat(intentos.bloqueada("1.1.1.1")).isFalse();
    }

    @Test
    void enProduccionNoArrancaConClavesDeEjemplo() {
        var debil = new AppProperties("549", "https://ggaming.com.ar", "admin", "admin", "cambiame-en-produccion", "uploads");
        assertThatThrownBy(() -> SecurityConfig.controlarClavesDeProduccion(debil))
                .hasMessageContaining("ADMIN_PASSWORD");

        var sinSecreto = new AppProperties("549", "https://ggaming.com.ar", "admin", "una-clave-bien-larga-123", "corta", "uploads");
        assertThatThrownBy(() -> SecurityConfig.controlarClavesDeProduccion(sinSecreto))
                .hasMessageContaining("SECRET_KEY");

        var bien = new AppProperties("549", "https://ggaming.com.ar", "admin", "una-clave-bien-larga-123",
                "f3a9c1e7b2d84f6a9c0e1b3d5f7a9c2e4b6d8f0a", "uploads");
        SecurityConfig.controlarClavesDeProduccion(bien); // no lanza
    }

    @Test
    void enDesarrolloSePermiteAdminAdmin() {
        var local = new AppProperties("549", "http://localhost:8080", "admin", "admin", "cambiame-en-produccion", "uploads");
        SecurityConfig.controlarClavesDeProduccion(local); // no lanza
    }
}
