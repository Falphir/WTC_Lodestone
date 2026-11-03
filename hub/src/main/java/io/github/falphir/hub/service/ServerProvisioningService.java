package io.github.falphir.hub.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creates a server on PufferPanel from one of its templates and registers it on the hub in the same
 * step: the panel server, the hub entry with its token and link, the mod config with that token, the
 * mod jar, and the template's install. What's left for the admin is the modpack's own files.
 * <p>
 * Order matters for what a failure leaves behind. Until the hub entry exists, a failure undoes the
 * panel server too, so nothing is half-made. After it, the server is real: a failed file write or
 * install is reported as a warning, since the admin can finish that one step by hand.
 */
@Service
public class ServerProvisioningService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerProvisioningService.class);
    static final String CONFIG_PATH = "config/wtc_lodestone-common.toml";

    /** {@code values}: template variable name → the value chosen in the form. {@code nodeId} null means the panel's first node. */
    public record CreateRequest(String template, Integer nodeId, String hubId, String name, Map<String, Object> values) {}

    /** {@code token} is shown once, like a registration's. */
    public record Created(String hubId, String name, String pufferpanelId, String token, List<String> warnings) {}

    /** A template's setting as the dashboard's form shows it. */
    public record Variable(String key, String display, String desc, String type, Object value, boolean required,
            List<?> options) {}

    public record TemplateView(String name, String display, List<Variable> variables) {}

    private final PufferPanelClient panel;
    private final ServerService servers;
    private final ModRelease modRelease;

    public ServerProvisioningService(PufferPanelClient panel, ServerService servers, ModRelease modRelease) {
        this.panel = panel;
        this.servers = servers;
        this.modRelease = modRelease;
    }

    public List<Map<String, Object>> templates() {
        requireConfigured();
        return call(panel::templates);
    }

    public List<Map<String, Object>> nodes() {
        requireConfigured();
        return call(panel::nodes);
    }

    /** A template's settings for the form; internal ones are the template's own business and left out. */
    public TemplateView template(String name) {
        requireConfigured();
        Map<String, Object> template = call(() -> panel.template(name));
        List<Variable> variables = new ArrayList<>();
        variablesOf(template).forEach((key, raw) -> {
            if (!(raw instanceof Map<?, ?> v) || Boolean.TRUE.equals(v.get("internal"))) return;
            variables.add(new Variable(key, string(v.get("display"), key), string(v.get("desc"), null),
                    string(v.get("type"), "string"), v.get("value"), Boolean.TRUE.equals(v.get("required")),
                    v.get("options") instanceof List<?> options ? options : List.of()));
        });
        return new TemplateView(name, string(template.get("display"), name), variables);
    }

    public Created create(CreateRequest request, String admin) {
        requireConfigured();
        String hubUrl = panel.serverHubUrl();
        if (hubUrl == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Set LODESTONE_PUFFERPANEL_SERVER_HUB_URL first: the hub's address as a game server reaches it");
        }
        if (servers.isRegistered(request.hubId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Server '" + request.hubId() + "' already exists");
        }

        Map<String, Object> body = new LinkedHashMap<>(call(() -> panel.template(request.template())));
        Map<String, Object> data = new LinkedHashMap<>(variablesOf(body));
        Map<String, Object> values = request.values() == null ? Map.of() : request.values();
        values.forEach((key, value) -> {
            if (!(data.get(key) instanceof Map<?, ?> variable) || Boolean.TRUE.equals(variable.get("internal"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The template '" + request.template() + "' has no setting '" + key + "'");
            }
            Map<String, Object> chosen = new HashMap<>();
            variable.forEach((k, v) -> chosen.put(String.valueOf(k), v));
            chosen.put("value", value);
            data.put(key, chosen);
        });
        body.put("data", data);
        body.remove("readme"); // the template's own notes, not part of a server
        body.put("name", request.name());
        body.put("node", request.nodeId() != null ? request.nodeId() : firstNode());
        body.put("users", List.of());
        if (body.get("environment") == null && body.get("supportedEnvironments") instanceof List<?> envs && !envs.isEmpty()) {
            body.put("environment", envs.get(0));
        }

        String panelId = call(() -> panel.createServer(body));
        ServerService.RegisteredServer registered;
        try {
            registered = servers.register(request.hubId(), request.name(),
                    new ServerService.Patch(null, null, null, null, null, null, null, null, null, panelId));
        } catch (RuntimeException e) {
            // nothing on the hub points at it, so don't leave it behind on the panel either
            try {
                panel.deleteServer(panelId);
            } catch (RuntimeException cleanup) {
                LOGGER.warn("Couldn't delete PufferPanel server {} after a failed registration: {}", panelId, cleanup.getMessage());
            }
            throw e;
        }

        List<String> warnings = new ArrayList<>();
        attempt(warnings, "write the mod config", () ->
                panel.putFile(panelId, CONFIG_PATH, modConfig(hubUrl, request.hubId(), registered.token()).getBytes(StandardCharsets.UTF_8)));
        attempt(warnings, "add the WTC Lodestone mod jar", () -> {
            ModRelease.Jar jar = modRelease.latestNeoForgeJar();
            panel.putFile(panelId, "mods/" + jar.fileName(), jar.bytes());
        });
        attempt(warnings, "start the install", () -> panel.install(panelId));

        LOGGER.info("{} created {} (PufferPanel {}) from template {}{}", admin, request.hubId(), panelId, request.template(),
                warnings.isEmpty() ? "" : ", with warnings: " + warnings);
        return new Created(request.hubId(), request.name(), panelId, registered.token(), warnings);
    }

    /** The mod's config file, in the exact format LodestoneConfig reads: one key = value a line, strings quoted. */
    static String modConfig(String hubUrl, String serverId, String token) {
        return """
                #Written by the WTC Lodestone hub when this server was created from the dashboard.
                enabled = true
                dryRun = false
                hubUrl = "%s"
                serverId = "%s"
                token = "%s"
                syncWhitelist = true
                syncBans = true
                whitelistMessage = ""
                """.formatted(hubUrl, serverId, token);
    }

    private void attempt(List<String> warnings, String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            warnings.add("Couldn't " + what + ": " + e.getMessage());
        }
    }

    private Object firstNode() {
        List<Map<String, Object>> nodes = call(panel::nodes);
        if (nodes.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PufferPanel has no nodes to put a server on");
        return nodes.get(0).get("id");
    }

    private void requireConfigured() {
        if (!panel.isConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PufferPanel isn't configured");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> variablesOf(Map<String, Object> template) {
        return template != null && template.get("data") instanceof Map<?, ?> data ? (Map<String, Object>) data : Map.of();
    }

    private static String string(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }

    /** A panel failure becomes a 502 worded for the admin. */
    private static <T> T call(Supplier<T> request) {
        try {
            T result = request.get();
            if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PufferPanel has no such thing");
            return result;
        } catch (PufferPanelClient.PanelException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }
}
