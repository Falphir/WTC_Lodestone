package io.github.falphir.hub.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Dashboard sign-in")
public class AuthController {

    /** Disabling or deleting an admin only locks them out once their current token expires. */
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(8);

    private final JwtEncoder encoder;

    public AuthController(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    @PostMapping("/token")
    @SecurityRequirement(name = "adminLogin")
    @Operation(summary = "Get an admin token",
            description = "Exchanges admin username + password (HTTP Basic) for a JWT to send as 'Authorization: Bearer <token>'.")
    public TokenResponse token(Authentication authentication) {
        // Renewing with a token would let a stolen one live forever; require the password.
        if (authentication instanceof JwtAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sign in with username and password to get a new token");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TOKEN_LIFETIME);
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("wtc-lodestone-hub")
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, expiresAt);
    }

    public record TokenResponse(String token, Instant expiresAt) {}
}
