package io.github.falphir.hub;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Dashboard files (the React app itself)
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/*.svg").permitAll()
                        // Health check and error pages
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .requestMatchers("/api/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Everything else stays locked
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}