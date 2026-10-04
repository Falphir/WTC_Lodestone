package io.github.falphir.hub.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.github.falphir.hub.entity.ServerHeartbeat;
import io.github.falphir.hub.service.ServerService;
import io.github.falphir.hub.service.WhitelistService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@RestController
@RequestMapping("/api/admin/servers")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminServerController {

    private final ServerService serverService;
    private final WhitelistService whitelistService;

    public AdminServerController(ServerService serverService, WhitelistService whitelistService) {
        this.serverService = serverService;
        this.whitelistService = whitelistService;
    }

    @GetMapping
    @Operation(summary = "List servers", description = "All registered servers, each with its most recent heartbeat (null if none yet).")
    public List<ServerView> list() {
        String whitelistVersion = whitelistService.version();
        return serverService.listServers().stream()
                .map(s -> {
                    ServerHeartbeat latest = serverService.latestHeartbeat(s.getId()).orElse(null);
                    return new ServerView(s.getId(), s.getName(), s.isEnabled(), s.getCreatedAt(), s.getLastSeen(),
                            serverService.status(s, latest), latest == null ? null : HeartbeatView.from(latest),
                            new ModView(s.getModVersion(), s.getMinecraftVersion(), s.getLoaderVersion()),
                            new WhitelistSyncView(s.getSyncWhitelist(), s.getWhitelistSyncedAt(),
                                    s.getWhitelistVersion() != null && s.getWhitelistVersion().equals(whitelistVersion)));
                })
                .toList();
    }

    public record ServerView(String id, String name, boolean enabled, Instant createdAt, Instant lastSeen,
            @Schema(description = "Decided by the hub from the last heartbeat") ServerService.ServerStatus status,
            HeartbeatView latest, ModView mod, WhitelistSyncView whitelist) {}

    /** What the mod reported on its last check-in; all null until it has checked in with a mod that reports them. */
    public record ModView(String version, String minecraftVersion, String loaderVersion) {}

    /**
     * @param enabled  the server's syncWhitelist setting, null until reported
     * @param syncedAt when it last applied a whitelist from the hub
     * @param current  whether what it applied is the hub's current whitelist
     */
    public record WhitelistSyncView(Boolean enabled, Instant syncedAt, boolean current) {}

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
    @Operation(summary = "Server heartbeat history", description = "Heartbeats from the last `hours` hours (1-168), most recent first.")
    public List<HeartbeatView> heartbeats(@PathVariable String id, @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours) {
        Instant since = Instant.now().minus(Duration.ofHours(hours));
        return serverService.heartbeatHistory(id, since).stream().map(HeartbeatView::from).toList();
    }

    public record HeartbeatView(
            Instant recordedAt, int playerCount, int maxPlayers, double tps, int memoryUsedMb, int memoryMaxMb) {
        static HeartbeatView from(ServerHeartbeat h) {
            return new HeartbeatView(h.getRecordedAt(), h.getPlayerCount(), h.getMaxPlayers(), h.getTps(),
                    h.getMemoryUsedMb(), h.getMemoryMaxMb());
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a server", description = "Also deletes its heartbeat history. It stops being able to authenticate immediately.")
    public void remove(@PathVariable String id) {
        serverService.remove(id);
    }
}


