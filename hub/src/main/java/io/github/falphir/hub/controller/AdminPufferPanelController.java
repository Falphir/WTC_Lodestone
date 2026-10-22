package io.github.falphir.hub.controller;

import io.github.falphir.hub.service.PufferPanelClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/pufferpanel")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminPufferPanelController {

    private final PufferPanelClient panel;

    public AdminPufferPanelController(PufferPanelClient panel) {
        this.panel = panel;
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

    public record ConnectionView(
            @Schema(description = "False until LODESTONE_PUFFERPANEL_URL, _CLIENT_ID and _CLIENT_SECRET are all set") boolean configured,
            @Schema(description = "Signed in and listed servers just now") boolean connected,
            String url,
            @Schema(description = "Servers the OAuth2 client can see") Integer serverCount,
            @Schema(description = "Why it couldn't connect, worded for an admin") String error) {}
}
