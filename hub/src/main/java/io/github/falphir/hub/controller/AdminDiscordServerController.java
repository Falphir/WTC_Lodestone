package io.github.falphir.hub.controller;

import java.time.Instant;
import java.util.List;

import io.github.falphir.hub.entity.DiscordServer;
import io.github.falphir.hub.service.DiscordBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/discord/servers")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminDiscordServerController {

    private final DiscordBotService discordBot;

    public AdminDiscordServerController(DiscordBotService discordBot) {
        this.discordBot = discordBot;
    }

    @GetMapping
    @Operation(summary = "List Discord /serverinfo entries",
            description = "Display-only server metadata (no bridge auth) -- edited by the bot's /server command.")
    public List<ServerView> list() {
        return discordBot.listServers().stream().map(ServerView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a /serverinfo entry")
    public ServerView add(@Valid @RequestBody AddServerRequest request) {
        return ServerView.from(discordBot.addServer(request.id(), request.name(), request.publicAddress(),
                request.modpack(), request.installUrl()));
    }

    public record AddServerRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9_-]{1,32}", message = "lowercase letters, numbers, _ and - only") String id,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String publicAddress,
            @Size(max = 100) String modpack,
            @Size(max = 300) String installUrl) {}

    @PatchMapping("/{id}")
    @Operation(summary = "Update a /serverinfo entry", description = "Only non-null fields are changed.")
    public ServerView update(@PathVariable String id, @Valid @RequestBody UpdateServerRequest request) {
        return ServerView.from(discordBot.updateServer(id, new DiscordBotService.ServerPatch(
                request.name(), request.publicAddress(), request.modpack(), request.version(), request.installUrl())));
    }

    public record UpdateServerRequest(
            @Size(max = 100) String name,
            @Size(max = 100) String publicAddress,
            @Size(max = 100) String modpack,
            @Size(max = 32) String version,
            @Size(max = 300) String installUrl) {}

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a /serverinfo entry")
    public void remove(@PathVariable String id) {
        discordBot.removeServer(id);
    }

    public record ServerView(String id, String name, String publicAddress, String modpack, String version,
            String installUrl, Instant createdAt) {
        static ServerView from(DiscordServer s) {
            return new ServerView(s.getId(), s.getName(), s.getPublicAddress(), s.getModpack(), s.getVersion(),
                    s.getInstallUrl(), s.getCreatedAt());
        }
    }
}
