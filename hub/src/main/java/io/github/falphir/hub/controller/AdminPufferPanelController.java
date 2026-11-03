package io.github.falphir.hub.controller;

import java.util.List;
import java.util.Map;

import io.github.falphir.hub.service.PufferPanelClient;
import io.github.falphir.hub.service.ServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public AdminPufferPanelController(PufferPanelClient panel, ServerService servers) {
        this.panel = panel;
        this.servers = servers;
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
