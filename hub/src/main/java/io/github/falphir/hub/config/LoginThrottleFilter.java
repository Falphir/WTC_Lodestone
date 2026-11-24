package io.github.falphir.hub.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.falphir.hub.service.LoginThrottle;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers 429 + Retry-After, before any password is checked, to a request that carries one while
 * {@link LoginThrottle} holds its IP back. The answer is the same whether or not the username exists.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private static final String BASIC = "Basic ";

    private final LoginThrottle throttle;

    public LoginThrottleFilter(LoginThrottle throttle) {
        this.throttle = throttle;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String username = attemptedUsername(request);
        if (username != null) {
            Duration wait = throttle.retryAfter(request.getRemoteAddr(), username);
            if (!wait.isZero()) {
                long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** The username a request is trying a password for, or null when it isn't trying one. */
    private static String attemptedUsername(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BASIC, 0, BASIC.length())) {
            try {
                String decoded = new String(Base64.getDecoder().decode(header.substring(BASIC.length()).trim()),
                        StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                return colon < 0 ? null : decoded.substring(0, colon); // malformed: BasicAuthenticationFilter rejects it
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if ("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath())) {
            String username = request.getParameter("username");
            return username == null ? "" : username;
        }
        return null;
    }
}
