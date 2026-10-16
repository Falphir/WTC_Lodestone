package io.github.falphir.hub.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

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

    // Tokens are only valid as long as the key that signed them. Set lodestone.jwt-secret
    // (LODESTONE_JWT_SECRET) and admins stay signed in across a restart, until the token's own 8h
    // expiry. Leave it unset and the key is random per boot, which signs everyone out on restart --
    // the safe default, since a secret that ships in the repo is no secret. Any passphrase will do:
    // it is hashed to the 32 bytes HMAC-SHA256 needs, so length is not the caller's problem.
    @Bean
    SecretKey jwtKey(LodestoneProperties properties) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.isBlank()) {
            log.info("No lodestone.jwt-secret set: signing admin tokens with a per-boot key, "
                    + "so restarting the hub signs every admin out.");
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            return new SecretKeySpec(bytes, "HmacSHA256");
        }
        return new SecretKeySpec(sha256(secret.getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
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
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health", "/error", "/api/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/bridge/**").hasRole("SERVER")
                        .requestMatchers("/api/admin/**", "/api/auth/**").hasRole("ADMIN")

                        .anyRequest().authenticated()

                )
                // A bare 401, with no WWW-Authenticate challenge: the browser answers that header
                // with its own credentials popup, which is not the dashboard's sign-in form and
                // cannot sign anyone in -- /api/auth/token hands out the JWT the dashboard needs.
                // Clients that use Basic here (the dashboard, the Discord bot, Swagger UI) all send
                // the header up front and never wait to be challenged for it.
                .httpBasic(basic -> basic.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
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

    /**
     * Bearer tokens on /api/bridge/** are server tokens, not JWTs, so the JWT check must not see them.
     * The live-updates WebSocket can't send headers, so only there the JWT may come as ?access_token=.
     */
    private static BearerTokenResolver adminBearerTokens() {
        DefaultBearerTokenResolver headerOnly = new DefaultBearerTokenResolver();
        DefaultBearerTokenResolver headerOrQuery = new DefaultBearerTokenResolver();
        headerOrQuery.setAllowUriQueryParameter(true);
        return request -> {
            String uri = request.getRequestURI();
            if (uri.startsWith("/api/bridge/")) return null;
            return uri.equals("/api/admin/live") ? headerOrQuery.resolve(request) : headerOnly.resolve(request);
        };
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
