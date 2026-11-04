package io.github.falphir.hub.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
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
        public PanelException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final LodestoneProperties.PufferPanel config;
    private final RestClient http;
    /** For the slow calls (a modpack's upload and unpacking), which would outlast the usual timeout. */
    private final RestClient bulk;

    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    @Autowired
    public PufferPanelClient(LodestoneProperties properties) {
        this(properties.pufferpanel(), RestClient.builder().requestFactory(timeout(Duration.ofSeconds(10))),
                RestClient.builder().requestFactory(timeout(Duration.ofMinutes(30))));
    }

    /** Takes the builder so tests can bind it to a mock server. */
    PufferPanelClient(LodestoneProperties.PufferPanel config, RestClient.Builder builder) {
        this(config, builder, builder);
    }

    private PufferPanelClient(LodestoneProperties.PufferPanel config, RestClient.Builder builder, RestClient.Builder bulkBuilder) {
        this.config = config;
        String base = isConfigured() ? config.url().replaceAll("/+$", "") : null;
        this.http = base != null ? builder.baseUrl(base).build() : null;
        this.bulk = base != null ? bulkBuilder.baseUrl(base).build() : null;
    }

    private static JdkClientHttpRequestFactory timeout(Duration read) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(read);
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

    /**
     * Sends a power action ({@code start}, {@code stop} or {@code kill}) to a panel server, without
     * {@code ?wait}: the panel answers at once and the server starts or stops in the background.
     */
    public void power(String panelId, String action) {
        post("/proxy/daemon/server/{id}/{action}", null, Void.class, panelId, action);
    }

    /** Whether the panel server's process is running right now. */
    public boolean isRunning(String panelId) {
        Map<?, ?> status = get("/proxy/daemon/server/{id}/status", Map.class, panelId);
        return status != null && Boolean.TRUE.equals(status.get("running"));
    }

    /** The hub's address as a game server sees it, for the mod config of servers it creates. Null when unset. */
    public String serverHubUrl() {
        return config == null || config.serverHubUrl() == null || config.serverHubUrl().isBlank() ? null : config.serverHubUrl();
    }

    /** Every template the panel has, as the panel describes them ({@code name}, {@code display}, ...). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> templates() {
        List<Map<String, Object>> all = get("/api/templates", List.class);
        return all == null ? List.of() : all;
    }

    /**
     * One template in full: its server definition ({@code data} variables, {@code install}, {@code run},
     * {@code environment}...), which is also exactly the shape a new server is created from.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> template(String name) {
        return get("/api/templates/{name}", Map.class, name);
    }

    /** The panel's nodes ({@code id}, {@code name}, {@code isLocal}...). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> nodes() {
        List<Map<String, Object>> all = get("/api/nodes", List.class);
        return all == null ? List.of() : all;
    }

    /** Creates a server from a template's definition plus {@code node}, {@code users} and {@code name}; returns its new id. */
    public String createServer(Map<String, Object> body) {
        Map<?, ?> created = post("/api/servers", body, Map.class);
        if (created == null || !(created.get("id") instanceof String id)) {
            throw new PanelException("PufferPanel created the server but didn't say its id", null);
        }
        return id;
    }

    /**
     * Writes a file into a panel server, creating it or replacing it. {@code path} is relative to the
     * server's folder, e.g. config/wtc_lodestone-common.toml; it's put in the URL as is, so its slashes
     * stay slashes. The panel won't create missing folders on a file write ("no such file or
     * directory" on a server that's only just been created), so the parent folder is made first with
     * {@code ?folder}, which makes the whole path like mkdir -p and is fine when it already exists.
     */
    public void putFile(String panelId, String path, byte[] content) {
        makeParentFolder(panelId, path);
        call(bearer -> http.put().uri("/proxy/daemon/server/{id}/file/" + path, panelId)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * As {@link #putFile(String, String, byte[])}, streaming a file from disk: for ones too big to hold
     * in memory. {@code progress} hears how much of it has been sent.
     */
    public void putFile(String panelId, String path, Path content, TransferProgress progress) {
        makeParentFolder(panelId, path);
        var counted = new FileSystemResource(content) {
            @Override
            public InputStream getInputStream() throws IOException {
                return new TransferProgress.Counting(super.getInputStream(), contentLength(), progress);
            }
        };
        call(bearer -> bulk.put().uri("/proxy/daemon/server/{id}/file/" + path, panelId)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(counted)
                .retrieve()
                .toBodilessEntity());
    }

    private void makeParentFolder(String panelId, String path) {
        int slash = path.lastIndexOf('/');
        if (slash > 0) {
            call(bearer -> http.put().uri("/proxy/daemon/server/{id}/file/" + path.substring(0, slash) + "?folder", panelId)
                    .header("Authorization", bearer)
                    .retrieve()
                    .toBodilessEntity());
        }
    }

    /**
     * Unpacks an archive that's in the server's folder into the server's root. The panel answers once
     * it's done, so this can take a while. It won't unpack over a file that's already there.
     */
    public void extract(String panelId, String archive) {
        call(bearer -> bulk.get().uri("/proxy/daemon/server/{id}/extract/" + archive, panelId)
                .header("Authorization", bearer)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * Deletes a folder in the server's folder, with everything in it. One that's already gone counts as
     * deleted. Only for folders: 2.7.1's delete lists the path as a folder first, so on a lone file it
     * fails with "readdirent: not a directory".
     */
    public void deleteFolder(String panelId, String path) {
        try {
            call(bearer -> http.delete().uri("/proxy/daemon/server/{id}/file/" + path, panelId)
                    .header("Authorization", bearer)
                    .retrieve()
                    .toBodilessEntity());
        } catch (PanelException e) {
            if (!(e.getCause() instanceof HttpClientErrorException.NotFound)) throw e;
        }
    }

    /** Runs the server's install steps (from its template) in the background. */
    public void install(String panelId) {
        power(panelId, "install");
    }

    /** Deletes a panel server, files and world included. One that's already gone counts as deleted. */
    public void deleteServer(String panelId) {
        try {
            call(bearer -> http.delete().uri("/api/servers/{id}", panelId).header("Authorization", bearer).retrieve().toBodilessEntity());
        } catch (PanelException e) {
            if (!(e.getCause() instanceof HttpClientErrorException.NotFound)) throw e;
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
