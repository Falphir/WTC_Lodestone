package io.github.falphir.hub.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.Authentication;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.falphir.hub.entity.GameServer;
import io.github.falphir.hub.service.ServerService;
import io.github.falphir.hub.service.WhitelistService;
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
    private final WhitelistService whitelistService;

    public BridgeController(ServerService serverService, WhitelistService whitelistService) {
        this.serverService = serverService;
        this.whitelistService = whitelistService;
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

    @GetMapping("/whitelist")
    @Operation(summary = "Network whitelist", description = "Polled by the mod, which mirrors it into the server's whitelist.json.")
    public List<WhitelistEntry> whitelist() {
        return whitelistService.list().stream().map(p -> new WhitelistEntry(p.getUuid(), p.getName())).toList();
    }

    /** Same shape as whitelist.json entries. */
    public record WhitelistEntry(String uuid, String name) {}

    public record HeartbeatRequest(
            @Min(0) int playerCount,
            @Min(0) int maxPlayers,
            @Min(0) double tps,
            @Min(0) int memoryUsedMb,
            @Min(0) int memoryMaxMb) {}
}