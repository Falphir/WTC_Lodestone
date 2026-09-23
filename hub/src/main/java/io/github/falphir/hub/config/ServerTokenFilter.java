package io.github.falphir.hub.config;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.falphir.hub.service.ServerService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ServerTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final ServerService serverService;

    public ServerTokenFilter(ServerService serverService) {
        this.serverService = serverService;
    }

    /** Only look at requests from game servers. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/bridge/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            serverService.authenticate(token).ifPresent(server -> {
                var authentication = new UsernamePasswordAuthenticationToken(
                        server.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_SERVER")));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}