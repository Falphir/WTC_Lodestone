package io.github.falphir.hub.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.github.falphir.hub.config.LodestoneProperties;

/**
 * The hub's client for PufferPanel's API, written against PufferPanel 2.7.1 (its routes differ in 3.x).
 * <p>
 * It signs in with an OAuth2 client's id and secret ({@code POST /oauth2/token}, form-encoded,
 * {@code client_credentials}) and keeps the hour-long token until shortly before it runs out. Panel
 * routes are under {@code /api}; daemon routes (start, stop, console, files...) must be called as
 * {@code /proxy/daemon/...}, since the panel won't take an OAuth token on {@code /daemon} directly.
 * <p>
 * Optional: with no URL, client id or secret configured, {@link #isConfigured()} is false and nothing
 * here ever calls out.
 */
@Component
public class PufferPanelClient {

    /** Fetch a new token this long before the old one would expire, so a slow call can't outlive it. */
    private static final Duration TOKEN_MARGIN = Duration.ofSeconds(60);
    private static final int PAGE_SIZE = 100;

    /** A server as the panel lists it. {@code id} is PufferPanel's own 8-character identifier. */
    public record PanelServer(String id, String name, Integer nodeId, String ip, Integer port, String type) {}

    record ServerPage(List<PanelServer> servers, Paging paging) {}

    record Paging(int page, int pageSize, long total) {}

    /** Why a call to the panel failed, worded for an admin. Never carries the secret or the token. */
    public static class PanelException extends RuntimeException {
        PanelException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final LodestoneProperties.PufferPanel config;
    private final RestClient http;

    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    @Autowired
    public PufferPanelClient(LodestoneProperties properties) {
        this(properties.pufferpanel(), RestClient.builder().requestFactory(timeouts()));
    }

    /** Takes the builder so tests can bind it to a mock server. */
    PufferPanelClient(LodestoneProperties.PufferPanel config, RestClient.Builder builder) {
        this.config = config;
        this.http = isConfigured() ? builder.baseUrl(config.url().replaceAll("/+$", "")).build() : null;
    }

    private static JdkClientHttpRequestFactory timeouts() {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    public boolean isConfigured() {
        return config != null && config.isConfigured();
    }

    /** The panel's address as configured, for showing admins where the hub is looking. Null when unset. */
    public String url() {
        return isConfigured() ? config.url() : null;
    }

    /** Every server the OAuth2 client can see, across all pages. */
    public List<PanelServer> listServers() {
        List<PanelServer> all = new ArrayList<>();
        for (int page = 1; ; page++) {
            ServerPage result = get("/api/servers?limit={limit}&page={page}", ServerPage.class, PAGE_SIZE, page);
            List<PanelServer> servers = result == null || result.servers() == null ? List.of() : result.servers();
            all.addAll(servers);
            if (servers.isEmpty() || result.paging() == null || all.size() >= result.paging().total()) return all;
        }
    }

    /** A GET against the panel, signed in. Later steps build their calls on this. */
    public <T> T get(String uri, Class<T> type, Object... uriVariables) {
        return call(bearer -> http.get().uri(uri, uriVariables).header("Authorization", bearer).retrieve().body(type));
    }

    /** A POST with a JSON body (or none), signed in. */
    public <T> T post(String uri, Object body, Class<T> type, Object... uriVariables) {
        return call(bearer -> {
            var request = http.post().uri(uri, uriVariables).header("Authorization", bearer);
            if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
            return request.retrieve().body(type);
        });
    }

    /** Runs a request with a valid token; a 401 means the token went stale early, so it gets one retry with a fresh one. */
    private <T> T call(Function<String, T> request) {
        if (!isConfigured()) throw new PanelException("PufferPanel isn't configured", null);
        try {
            return request.apply("Bearer " + token(false));
        } catch (HttpClientErrorException.Unauthorized stale) {
            try {
                return request.apply("Bearer " + token(true));
            } catch (RestClientException e) {
                throw failure(e);
            }
        } catch (RestClientException e) {
            throw failure(e);
        }
    }

    private synchronized String token(boolean forceNew) {
        if (!forceNew && token != null && Instant.now().isBefore(tokenExpires)) return token;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        Map<?, ?> body;
        try {
            body = http.post().uri("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.Unauthorized e) {
            throw new PanelException("PufferPanel rejected the client id/secret", e);
        } catch (RestClientException e) {
            throw failure(e);
        }
        if (body == null || !(body.get("access_token") instanceof String accessToken) || accessToken.isBlank()) {
            throw new PanelException("PufferPanel gave no token: " + (body == null ? "empty answer" : body.get("error")), null);
        }
        long expiresIn = body.get("expires_in") instanceof Number n ? n.longValue() : 3600;
        token = accessToken;
        tokenExpires = Instant.now().plusSeconds(expiresIn).minus(TOKEN_MARGIN);
        return token;
    }

    private PanelException failure(RestClientException e) {
        if (e instanceof HttpClientErrorException http) {
            return new PanelException("PufferPanel refused the request (HTTP " + http.getStatusCode().value() + ")", e);
        }
        if (e instanceof HttpServerErrorException http) {
            return new PanelException("PufferPanel failed the request (HTTP " + http.getStatusCode().value() + ")", e);
        }
        return new PanelException("PufferPanel unreachable at " + config.url(), e);
    }
}
