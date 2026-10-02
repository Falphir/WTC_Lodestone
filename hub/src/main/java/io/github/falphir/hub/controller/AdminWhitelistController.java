package io.github.falphir.hub.controller;

import java.time.Instant;
import java.util.List;

import io.github.falphir.hub.entity.WhitelistedPlayer;
import io.github.falphir.hub.service.WhitelistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/whitelist")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminWhitelistController {

    private static final String UUID_REGEX = "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}";

    private final WhitelistService whitelist;

    public AdminWhitelistController(WhitelistService whitelist) {
        this.whitelist = whitelist;
    }

    @GetMapping
    @Operation(summary = "List whitelisted players", description = "The network-wide whitelist, sorted by name.")
    public List<PlayerView> list() {
        return whitelist.list().stream().map(PlayerView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Whitelist a player",
            description = "Looks up the Minecraft account by name and adds it. Servers pick it up within a minute.")
    public PlayerView add(Authentication authentication, @Valid @RequestBody AddPlayerRequest request) {
        return PlayerView.from(whitelist.add(request.name(), authentication.getName()));
    }

    @PostMapping("/import")
    @Operation(summary = "Import a whitelist.json",
            description = "Adds every entry of an existing server whitelist. Players already on the list are skipped.")
    public ImportResult importWhitelist(Authentication authentication, @Valid @RequestBody ImportRequest request) {
        List<WhitelistService.Entry> entries = request.players().stream()
                .map(p -> new WhitelistService.Entry(p.uuid(), p.name()))
                .toList();
        return new ImportResult(whitelist.importEntries(entries, authentication.getName()));
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a player from the whitelist")
    public void remove(@PathVariable String uuid) {
        whitelist.remove(uuid);
    }

    public record AddPlayerRequest(
            @Schema(description = "Minecraft username", example = "Notch")
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{1,16}", message = "not a valid Minecraft username") String name) {}

    public record ImportRequest(
            @Schema(description = "Contents of a server's whitelist.json")
            @NotNull @Size(max = 10_000) List<@Valid ImportEntry> players) {}

    public record ImportEntry(
            @NotBlank @Pattern(regexp = UUID_REGEX, message = "not a UUID") String uuid,
            @NotBlank @Size(max = 16) String name) {}

    public record ImportResult(int added) {}

    public record PlayerView(String uuid, String name, String addedBy, Instant addedAt) {
        static PlayerView from(WhitelistedPlayer p) {
            return new PlayerView(p.getUuid(), p.getName(), p.getAddedBy(), p.getAddedAt());
        }
    }
}
