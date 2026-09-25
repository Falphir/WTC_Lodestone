package io.github.falphir.hub.controller;

import java.time.Instant;
import java.util.List;

import io.github.falphir.hub.entity.ServerHeartbeat;
import io.github.falphir.hub.service.ServerService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@RestController
@RequestMapping("/api/admin/servers")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
public class AdminServerController {

    private final ServerService serverService;

    public AdminServerController(ServerService serverService) {
        this.serverService = serverService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a game server",
            description = "Creates a server and returns its token. The token is shown only once.")
    public ServerService.RegisteredServer register(@Valid @RequestBody RegisterServerRequest request) {
        return serverService.register(request.id(), request.name());
    }

    public record RegisterServerRequest(
            @Schema(description = "Unique server id, also used as serverId in the mod config", example = "exampleserver")
            @NotBlank @Pattern(regexp = "[a-z0-9_-]{1,64}", message = "lowercase letters, numbers, _ and - only") String id,

            @Schema(description = "Display name shown on the dashboard and in Discord", example = "Example Server")
            @NotBlank @Size(max = 100) String name) {}

    @GetMapping("/{id}/heartbeats")
    @Operation(summary = "Server heartbeat history", description = "Most recent heartbeats first.")
    public List<HeartbeatView> heartbeats(@PathVariable String id) {
        return serverService.heartbeatHistory(id).stream().map(HeartbeatView::from).toList();
    }

    public record HeartbeatView(
            Instant recordedAt, int playerCount, int maxPlayers, double tps, int memoryUsedMb, int memoryMaxMb) {
        static HeartbeatView from(ServerHeartbeat h) {
            return new HeartbeatView(h.getRecordedAt(), h.getPlayerCount(), h.getMaxPlayers(), h.getTps(),
                    h.getMemoryUsedMb(), h.getMemoryMaxMb());
        }
    }
}


