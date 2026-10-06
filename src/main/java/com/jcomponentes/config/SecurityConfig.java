package com.jcomponentes.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SEGURIDAD del panel admin (Spring Security):
 *  - Todo lo que empieza con /admin pide login, salvo la propia página de login.
 *  - La tienda pública queda abierta.
 *  - Los formularios llevan un token CSRF automático: otro sitio no puede
 *    hacer que tu navegador borre productos "a escondidas".
 *  - Las contraseñas se comparan con BCrypt (nunca en texto plano).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String LOGIN = "/admin/login";

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, AppProperties config, IntentosLogin intentos) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(LOGIN).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginPage(LOGIN)
                .loginProcessingUrl(LOGIN)
                .successHandler((req, res, auth) -> {
                    intentos.exito(req.getRemoteAddr());
                    res.sendRedirect(req.getContextPath() + "/admin");
                })
                .failureHandler((req, res, ex) -> {
                    intentos.fallo(req.getRemoteAddr());
                    res.sendRedirect(req.getContextPath() + LOGIN + "?error");
                }))
            // "Recordarme": cookie firmada con SECRET_KEY, dura 30 días.
            .rememberMe(r -> r
                .key(config.secretKey())
                .rememberMeParameter("recordarme")
                .tokenValiditySeconds(30 * 24 * 3600))
            .logout(l -> l
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl(LOGIN + "?salio")
                .deleteCookies("JSESSIONID", "remember-me"))
            // Antes de intentar el login, revisamos si esa IP está bloqueada.
            .addFilterBefore(new BloqueoLoginFilter(intentos), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** El único usuario: el dueño. Usuario y contraseña salen de las variables de entorno. */
    @Bean
    UserDetailsService usuarios(AppProperties config, PasswordEncoder encoder) {
        if ("admin".equals(config.adminPassword())) {
            log.warn("⚠️  ADMIN_PASSWORD tiene el valor por defecto 'admin'. Cambialo antes de publicar la app.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(config.adminUser())
                .password(encoder.encode(config.adminPassword()))
                .roles("ADMIN")
                .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Si la IP superó los intentos permitidos, ni siquiera revisamos la contraseña. */
    static class BloqueoLoginFilter extends OncePerRequestFilter {
        private final IntentosLogin intentos;

        BloqueoLoginFilter(IntentosLogin intentos) {
            this.intentos = intentos;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws java.io.IOException, jakarta.servlet.ServletException {
            boolean esLogin = "POST".equals(req.getMethod())
                    && (req.getContextPath() + LOGIN).equals(req.getRequestURI());
            if (esLogin && intentos.bloqueada(req.getRemoteAddr())) {
                res.sendRedirect(req.getContextPath() + LOGIN + "?bloqueado");
                return;
            }
            chain.doFilter(req, res);
        }
    }
}
