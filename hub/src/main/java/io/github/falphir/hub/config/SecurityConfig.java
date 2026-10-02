package io.github.falphir.hub.config;

import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.github.falphir.hub.service.ServerService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
@EnableConfigurationProperties(LodestoneProperties.class)
public class SecurityConfig {

    // Admin accounts (io.github.falphir.hub.entity.AdminUser) are looked up by
    // AdminUserDetailsService, picked up automatically as the UserDetailsService bean.
    // AdminUserSeeder creates the accounts listed in lodestone.admins on startup.
    //
    // Admins exchange username + password (HTTP Basic) for a JWT at /api/auth/token, which the
    // dashboard then sends as a Bearer token. Game servers also send Bearer tokens, but on
    // /api/bridge/** only, where ServerTokenFilter handles them instead.

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ponytail: a fresh random key on every startup, so restarting the hub signs every admin out.
    // Load it from an env var instead if that gets annoying or the hub ever runs as several instances.
    @Bean
    SecretKey jwtKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        return NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ServerService serverService) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/*.svg").permitAll()
                        .requestMatchers("/actuator/health", "/error", "/api/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/bridge/**").hasRole("SERVER")
                        .requestMatchers("/api/admin/**", "/api/auth/**").hasRole("ADMIN")

                        .anyRequest().authenticated()

                )
                .httpBasic(Customizer.withDefaults())
                .formLogin(Customizer.withDefaults())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(adminBearerTokens())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesFromJwt())))
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .addFilterBefore(new ServerTokenFilter(serverService), BasicAuthenticationFilter.class);
        return http.build();
    }

    /** Bearer tokens on /api/bridge/** are server tokens, not JWTs, so the JWT check must not see them. */
    private static BearerTokenResolver adminBearerTokens() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith("/api/bridge/") ? null : resolver.resolve(request);
    }

    /** AuthController puts the admin's roles in a "roles" claim; turn them back into ROLE_ authorities. */
    private static JwtAuthenticationConverter rolesFromJwt() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
