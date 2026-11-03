package io.github.falphir.hub.controller;

import java.util.List;
import java.util.Map;

import io.github.falphir.hub.service.PufferPanelClient;
import io.github.falphir.hub.service.ServerProvisioningService;
import io.github.falphir.hub.service.ServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/pufferpanel")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminPufferPanelController {

    private final PufferPanelClient panel;
    private final ServerService servers;
    private final ServerProvisioningService provisioning;

    public AdminPufferPanelController(PufferPanelClient panel, ServerService servers, ServerProvisioningService provisioning) {
        this.panel = panel;
        this.servers = servers;
        this.provisioning = provisioning;
    }

    @GetMapping
    @Operation(summary = "PufferPanel connection",
            description = "Whether the hub is set up to reach PufferPanel, and if so whether it can sign in and list servers "
                    + "right now. Always 200: a failure is reported in `error`, not as an error response.")
    public ConnectionView connection() {
        if (!panel.isConfigured()) return new ConnectionView(false, false, null, null, null);
        try {
            return new ConnectionView(true, true, panel.url(), panel.listServers().size(), null);
        } catch (PufferPanelClient.PanelException e) {
            return new ConnectionView(true, false, panel.url(), null, e.getMessage());
        }
    }

    @GetMapping("/servers")
    @Operation(summary = "PufferPanel servers",
            description = "Every server the hub's PufferPanel client can see, each with the hub server it's linked to (null if none). "
                    + "Empty when PufferPanel isn't configured; 502 if it is but can't be reached.")
    public List<PanelServerView> servers() {
        if (!panel.isConfigured()) return List.of();
        List<PufferPanelClient.PanelServer> onPanel;
        try {
            onPanel = panel.listServers();
        } catch (PufferPanelClient.PanelException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
        Map<String, String> linkedTo = servers.panelLinks();
        return onPanel.stream()
                .map(s -> new PanelServerView(s.id(), s.name(), s.nodeId(), s.ip(), s.port(), s.type(), linkedTo.get(s.id())))
                .toList();
    }

    @GetMapping("/templates")
    @Operation(summary = "PufferPanel templates", description = "What a new server can be created from: each template's name and display name.")
    public List<TemplateSummary> templates() {
        return provisioning.templates().stream()
                .map(t -> new TemplateSummary(String.valueOf(t.get("name")),
                        t.get("display") instanceof String d && !d.isBlank() ? d : String.valueOf(t.get("name"))))
                .toList();
    }

    public record TemplateSummary(String name, String display) {}

    @GetMapping("/templates/{name}")
    @Operation(summary = "A PufferPanel template's settings",
            description = "The settings a server made from this template takes (memory, port, versions... whatever the template defines), "
                    + "with its defaults. Internal settings are left out.")
    public ServerProvisioningService.TemplateView template(@PathVariable String name) {
        return provisioning.template(name);
    }

    @GetMapping("/nodes")
    @Operation(summary = "PufferPanel nodes", description = "Where a new server can run.")
    public List<NodeView> nodes() {
        return provisioning.nodes().stream()
                .map(n -> new NodeView(n.get("id") instanceof Number id ? id.intValue() : 0, String.valueOf(n.get("name"))))
                .toList();
    }

    public record NodeView(int id, String name) {}

    @PostMapping("/servers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a server on PufferPanel and register it on the hub",
            description = "Creates the PufferPanel server from a template with the chosen settings, registers it on the hub linked to it, "
                    + "writes its mod config (with the new token) and the WTC Lodestone mod jar, and starts the template's install. "
                    + "The token is shown only once. Steps after registration that fail come back in `warnings` rather than "
                    + "undoing the server. The modpack's own files are uploaded separately.")
    public ServerProvisioningService.Created create(Authentication authentication, @Valid @RequestBody CreateServerRequest request) {
        return provisioning.create(new ServerProvisioningService.CreateRequest(request.template(), request.nodeId(),
                request.id(), request.name().trim(), request.values()), authentication.getName());
    }

    public record CreateServerRequest(
            @Schema(description = "PufferPanel template to create it from") @NotBlank @Size(max = 100) String template,
            @Schema(description = "PufferPanel node; omit for the first one") Integer nodeId,
            @Schema(description = "Unique hub server id, also its serverId in the mod config", example = "exampleserver")
            @NotBlank @Pattern(regexp = "[a-z0-9_-]{1,64}", message = "lowercase letters, numbers, _ and - only") String id,
            @Schema(description = "Display name on the dashboard, in Discord and on PufferPanel") @NotBlank @Size(max = 40) String name,
            @Schema(description = "Template setting → chosen value; settings left out keep the template's default") Map<String, Object> values) {}

    public record PanelServerView(String id, String name, Integer nodeId, String ip, Integer port,
            @Schema(description = "PufferPanel template type, e.g. minecraft-java") String type,
            @Schema(description = "Id of the hub server linked to it, null if none") String linkedTo) {}

    public record ConnectionView(
            @Schema(description = "False until LODESTONE_PUFFERPANEL_URL, _CLIENT_ID and _CLIENT_SECRET are all set") boolean configured,
            @Schema(description = "Signed in and listed servers just now") boolean connected,
            String url,
            @Schema(description = "Servers the OAuth2 client can see") Integer serverCount,
            @Schema(description = "Why it couldn't connect, worded for an admin") String error) {}
}
