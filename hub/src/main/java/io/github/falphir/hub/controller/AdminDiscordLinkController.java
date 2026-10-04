package io.github.falphir.hub.controller;

import java.util.List;
import java.util.Optional;

import io.github.falphir.hub.entity.DiscordLink;
import io.github.falphir.hub.service.DiscordLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/discord/links")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminDiscordLinkController {

    private static final String UUID_REGEX = "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}";

    private final DiscordLinkService service;

    public AdminDiscordLinkController(DiscordLinkService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List Discord<->Minecraft account links")
    public List<LinkView> list() {
        return service.listLinks().stream().map(LinkView::from).toList();
    }

    @GetMapping("/by-discord/{discordId}")
    @Operation(summary = "Find a link by Discord id")
    public ResponseEntity<LinkView> byDiscord(@PathVariable String discordId) {
        return found(service.findLinkByDiscordId(discordId));
    }

    @GetMapping("/by-uuid/{uuid}")
    @Operation(summary = "Find a link by Minecraft UUID")
    public ResponseEntity<LinkView> byUuid(@PathVariable String uuid) {
        return found(service.findLinkByUuid(uuid));
    }

    @GetMapping("/by-name/{name}")
    @Operation(summary = "Find a link by Minecraft username", description = "Case-insensitive.")
    public ResponseEntity<LinkView> byName(@PathVariable String name) {
        return found(service.findLinkByName(name));
    }

    private static ResponseEntity<LinkView> found(Optional<DiscordLink> link) {
        return link.map(LinkView::from).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{discordId}")
    @Operation(summary = "Set (or replace) a Discord account's link")
    public LinkView set(@PathVariable String discordId, @Valid @RequestBody SetLinkRequest request) {
        return LinkView.from(service.setLink(discordId, request.uuid(), request.username()));
    }

    public record SetLinkRequest(
            @NotBlank @Pattern(regexp = UUID_REGEX, message = "not a UUID") String uuid,
            @NotBlank @Size(max = 16) String username) {}

    @DeleteMapping("/{discordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a Discord account's link", description = "A no-op if there wasn't one.")
    public void remove(@PathVariable String discordId) {
        service.removeLink(discordId);
    }

    public record LinkView(String discordId, String uuid, String username, long linkedAt) {
        static LinkView from(DiscordLink l) {
            return new LinkView(l.getDiscordId(), l.getMcUuid(), l.getMcUsername(), l.getLinkedAt());
        }
    }
}
