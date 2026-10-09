package io.github.falphir.hub.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.falphir.hub.entity.GameServer;
import io.github.falphir.hub.entity.WhitelistedPlayer;
import io.github.falphir.hub.service.ServerService;
import io.github.falphir.hub.service.WhitelistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/bridge")
@Tag(name = "Bridge", description = "Endpoints called by the WTC Lodestone mod")
@SecurityRequirement(name = "serverToken")
public class BridgeController {

    /** Response header carrying the whitelist version; the mod echoes it back once applied. */
    public static final String WHITELIST_VERSION_HEADER = "X-Whitelist-Version";

    private final ServerService serverService;
    private final WhitelistService whitelistService;

    public BridgeController(ServerService serverService, WhitelistService whitelistService) {
        this.serverService = serverService;
        this.whitelistService = whitelistService;
    }

    @PostMapping("/hello")
    @Operation(summary = "Server check-in",
            description = "Called by the mod on startup with its versions and settings. Returns how often to send heartbeats.")
    public HelloResponse hello(Authentication authentication, @Valid @RequestBody(required = false) HelloRequest request) {
        HelloRequest r = request != null ? request : new HelloRequest(null, null, null, null, null);
        GameServer server = serverService.checkIn(authentication.getName(),
                r.modVersion(), r.minecraftVersion(), r.loaderVersion(), r.syncWhitelist(), r.enforceWhitelist());
        return new HelloResponse(server.getId(), server.getName(), "Connected to WTC Lodestone",
                serverService.heartbeatInterval().toSeconds());
    }

    public record HelloRequest(
            @Size(max = 32) String modVersion,
            @Size(max = 32) String minecraftVersion,
            @Size(max = 32) String loaderVersion,
            Boolean syncWhitelist,
            /** The server's own enforce-whitelist setting; null from mods older than this field. */
            Boolean enforceWhitelist) {}

    public record HelloResponse(String serverId, String name, String message, long heartbeatIntervalSeconds) {}

    @PostMapping("/heartbeat")
    @Operation(summary = "Server heartbeat", description = "Called periodically by the mod with the server's current stats.")
    public void heartbeat(Authentication authentication, @Valid @RequestBody HeartbeatRequest request) {
        List<ServerService.OnlinePlayer> players = request.players() == null ? List.of()
                : request.players().stream().map(p -> new ServerService.OnlinePlayer(p.uuid(), p.name())).toList();
        serverService.recordHeartbeat(authentication.getName(), request.playerCount(), request.maxPlayers(),
                request.tps(), request.msptAvg(), request.memoryUsedMb(), request.memoryMaxMb(), players);
    }

    public record HeartbeatRequest(
            @Min(0) int playerCount,
            @Min(0) int maxPlayers,
            @Min(0) double tps,
            /** Average ms per tick. 0 from mods older than this field, which only sent the derived tps. */
            @Min(0) double msptAvg,
            @Min(0) int memoryUsedMb,
            @Min(0) int memoryMaxMb,
            /** Who is on right now. Null from mods older than this field; the hub keeps it in memory only. */
            @Size(max = 1000) List<@Valid OnlinePlayerRequest> players) {}

    public record OnlinePlayerRequest(@NotBlank @Size(max = 36) String uuid, @NotBlank @Size(max = 16) String name) {}

    @GetMapping("/whitelist")
    @Operation(summary = "Network whitelist",
            description = "Polled by the mod, which mirrors it into the server's whitelist.json. "
                    + "The " + WHITELIST_VERSION_HEADER + " header identifies this version of the list. "
                    + "204 (no body) means nobody has ever been added to the hub whitelist yet -- "
                    + "the mod should leave the server's existing whitelist alone rather than wipe it.")
    public ResponseEntity<List<WhitelistEntry>> whitelist() {
        if (!whitelistService.everPopulated()) {
            return ResponseEntity.noContent().build();
        }
        List<WhitelistedPlayer> players = whitelistService.list();
        return ResponseEntity.ok()
                .header(WHITELIST_VERSION_HEADER, whitelistService.version(players))
                .body(players.stream().map(p -> new WhitelistEntry(p.getUuid(), p.getName())).toList());
    }

    /** Same shape as whitelist.json entries. */
    public record WhitelistEntry(String uuid, String name) {}

    @PostMapping("/whitelist/applied")
    @Operation(summary = "Whitelist applied", description = "The mod applied this whitelist version to the server.")
    public void whitelistApplied(Authentication authentication, @Valid @RequestBody WhitelistAppliedRequest request) {
        serverService.whitelistApplied(authentication.getName(), request.version());
    }

    public record WhitelistAppliedRequest(@NotBlank @Size(max = 64) String version) {}
}
