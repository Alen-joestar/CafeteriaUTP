package com.utp.cafeteria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.utp.cafeteria.repository.UsuarioRepository;
import com.utp.cafeteria.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UsuarioRepository usuarios) {
        return identificador -> usuarios.buscarPorIdentificador(identificador)
                .map(usuario -> User.withUsername(usuario.getCodigo())
                        .password(usuario.getPassword())
                        .roles(usuario.getRol())
                        .build())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(
                        "Usuario no encontrado"));
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFiltro) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/productos", "/api/productos/**").permitAll()

                        .requestMatchers("/api/usuarios", "/api/usuarios/**").hasRole("CAFETERIA")
                        .requestMatchers(HttpMethod.POST, "/api/productos", "/api/productos/**").hasRole("CAFETERIA")
                        .requestMatchers(HttpMethod.PUT, "/api/productos/**").hasRole("CAFETERIA")
                        .requestMatchers(HttpMethod.DELETE, "/api/productos/**").hasRole("CAFETERIA")
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/**").hasRole("CAFETERIA")
                        .requestMatchers(HttpMethod.DELETE, "/api/pedidos/**").hasRole("CAFETERIA")

                        .requestMatchers(HttpMethod.GET, "/api/pedidos", "/api/pedidos/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/pedidos", "/api/pedidos/**").authenticated()

                        .anyRequest().denyAll())

                .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class)
                .headers(h -> h.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
