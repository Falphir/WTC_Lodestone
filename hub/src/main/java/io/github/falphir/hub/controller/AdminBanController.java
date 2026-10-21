package io.github.falphir.hub.controller;

import java.time.Instant;
import java.util.List;

import io.github.falphir.hub.entity.NetworkBan;
import io.github.falphir.hub.service.BanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bans")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminBanController {

    private final BanService bans;

    public AdminBanController(BanService bans) {
        this.bans = bans;
    }

    @GetMapping("/message")
    @Operation(summary = "Ban message",
            description = "What a network-banned player sees when refused or kicked. &-codes are colors; {reason} and {expires} "
                    + "are filled in per player. Bans a server made itself keep Minecraft's own message.")
    public BanMessage message() {
        return new BanMessage(bans.message());
    }

    @PutMapping("/message")
    @Operation(summary = "Set the ban message", description = "Blank goes back to the default. Servers pick it up on their next heartbeat.")
    public BanMessage setMessage(@Valid @RequestBody BanMessage request) {
        bans.setMessage(request.message());
        return new BanMessage(bans.message());
    }

    public record BanMessage(@Size(max = 500) String message) {}

    @GetMapping
    @Operation(summary = "List network bans",
            description = "Every ban, sorted by name. Expired ones stay listed (flagged) until the nightly cleanup removes them.")
    public List<BanView> list() {
        Instant now = Instant.now();
        return bans.list().stream().map(b -> BanView.from(b, now)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ban a player on every server",
            description = "Looks up the Minecraft account by name. Banning someone already banned replaces their ban. "
                    + "Servers apply it within seconds and kick the player if they're on.")
    public BanView ban(Authentication authentication, @Valid @RequestBody BanRequest request) {
        return BanView.from(bans.ban(request.name(), request.reason(), request.expiresAt(), authentication.getName()), Instant.now());
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Lift a network ban", description = "404 if the player isn't banned.")
    public void unban(@PathVariable String uuid) {
        bans.unban(uuid);
    }

    public record BanRequest(
            @Schema(description = "Minecraft username", example = "Notch")
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{1,16}", message = "not a valid Minecraft username") String name,
            @Schema(description = "Shown to staff; the player sees vanilla's ban message") @Size(max = 255) String reason,
            @Schema(description = "When the ban lifts itself; omit for a permanent ban") @Future Instant expiresAt) {}

    public record BanView(String uuid, String name, String reason, String bannedBy, Instant bannedAt,
            @Schema(description = "Null is permanent") Instant expiresAt, boolean expired) {
        static BanView from(NetworkBan b, Instant now) {
            return new BanView(b.getUuid(), b.getName(), b.getReason(), b.getBannedBy(), b.getBannedAt(), b.getExpiresAt(),
                    b.isExpired(now));
        }
    }
}
