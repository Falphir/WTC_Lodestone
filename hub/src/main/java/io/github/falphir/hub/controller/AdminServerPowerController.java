package io.github.falphir.hub.controller;

import io.github.falphir.hub.service.ServerPowerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/servers/{id}/power")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminServerPowerController {

    private final ServerPowerService power;

    public AdminServerPowerController(ServerPowerService power) {
        this.power = power;
    }

    @GetMapping
    @Operation(summary = "A server's PufferPanel process state",
            description = "Whether the linked PufferPanel server is running, and whether a restart is under way. "
                    + "409 if the server isn't linked to PufferPanel, 503 if PufferPanel isn't configured.")
    public ServerPowerService.PowerStatus status(@PathVariable String id) {
        return power.status(id);
    }

    @PostMapping("/{action}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start, stop, restart or kill a server on PufferPanel",
            description = "`action` is start, stop, restart or kill. The panel acts in the background, so this answers "
                    + "202 straight away. A restart stops the server, waits up to 3 minutes for it to stop, then starts it; "
                    + "409 while one is already under way.")
    public PowerAction act(Authentication authentication, @PathVariable String id, @PathVariable String action) {
        power.act(id, action, authentication.getName());
        return new PowerAction(action);
    }

    public record PowerAction(String action) {}
}
