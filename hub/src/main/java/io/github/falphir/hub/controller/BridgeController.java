package io.github.falphir.hub.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.falphir.hub.entity.GameServer;
import io.github.falphir.hub.service.ServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/bridge")
@Tag(name = "Bridge", description = "Endpoints called by the WTC Lodestone mod")
@SecurityRequirement(name = "serverToken")
public class BridgeController {

    private final ServerService serverService;

    public BridgeController(ServerService serverService) {
        this.serverService = serverService;
    }

    @PostMapping("/hello")
    @Operation(summary = "Server check-in", description = "Called by the mod on startup. Requires the server's bearer token.")
    public HelloResponse hello(Authentication authentication) {
        GameServer server = serverService.markSeen(authentication.getName());
        return new HelloResponse(server.getId(), server.getName(), "Connected to WTC Lodestone");
    }

    public record HelloResponse(String serverId, String name, String message) {}

    @PostMapping("/heartbeat")
    @Operation(summary = "Server heartbeat", description = "Called periodically by the mod with the server's current stats.")
    public void heartbeat(Authentication authentication, @Valid @RequestBody HeartbeatRequest request) {
        serverService.recordHeartbeat(authentication.getName(), request.playerCount(), request.maxPlayers(),
                request.tps(), request.memoryUsedMb(), request.memoryMaxMb());
    }

    public record HeartbeatRequest(
            @Min(0) int playerCount,
            @Min(0) int maxPlayers,
            @Min(0) double tps,
            @Min(0) int memoryUsedMb,
            @Min(0) int memoryMaxMb) {}
}