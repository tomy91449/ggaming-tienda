package com.ggaming.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorPorIpTest {

    @Test
    void frenaAlSuperarElLimiteYSeLiberaAlMinutoSiguiente() {
        LimitadorPorIp limitador = new LimitadorPorIp(3);
        long ahora = 1_000_000_000L;
        assertThat(limitador.permitir("1.1.1.1", ahora)).isTrue();
        assertThat(limitador.permitir("1.1.1.1", ahora)).isTrue();
        assertThat(limitador.permitir("1.1.1.1", ahora)).isTrue();
        assertThat(limitador.permitir("1.1.1.1", ahora)).isFalse();          // el 4º en el mismo minuto
        assertThat(limitador.permitir("2.2.2.2", ahora)).isTrue();           // otra IP no se ve afectada
        assertThat(limitador.permitir("1.1.1.1", ahora + 60_000)).isTrue();  // minuto siguiente
    }
}
