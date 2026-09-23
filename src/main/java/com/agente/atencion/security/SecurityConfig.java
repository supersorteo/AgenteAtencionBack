package com.agente.atencion.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) { this.jwtFilter = jwtFilter; }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(c -> c.configurationSource(corsSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Siempre públicos
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/v1/chat/**").permitAll()
                // Landing: GET de catálogo y disponibilidad sin auth
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/servicios/**",
                    "/api/v1/barberos/**",
                    "/api/v1/galeria/**",
                    "/api/v1/disponibilidad/**",
                    "/api/v1/config/**",
                    "/uploads/**").permitAll()
                // Reserva desde el landing sin auth (visitantes pueden reservar)
                .requestMatchers(HttpMethod.POST, "/api/v1/turnos/**").permitAll()
                // Dashboard solo para admin
                .requestMatchers("/api/v1/dashboard/**").hasRole("ADMIN")
                // Todo lo demás requiere token
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        String extra = System.getenv().getOrDefault("ALLOWED_ORIGINS", "");
        List<String> patterns = new java.util.ArrayList<>(List.of(
            "http://localhost:4200",
            "http://localhost:3000",
            "https://*.vercel.app"
        ));
        if (!extra.isBlank()) {
            for (String o : extra.split(",")) { patterns.add(o.trim()); }
        }
        cfg.setAllowedOriginPatterns(patterns);
        cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization","Content-Type","Accept"));
        // No seteamos allowCredentials porque usamos JWT en header, no cookies
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
