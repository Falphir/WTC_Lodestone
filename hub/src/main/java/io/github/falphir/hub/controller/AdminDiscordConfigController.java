package io.github.falphir.hub.controller;

import java.util.List;

import io.github.falphir.hub.entity.DiscordConfig;
import io.github.falphir.hub.service.DiscordBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/discord/config")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminDiscordConfigController {

    private static final String SNOWFLAKE = "\\d{1,20}";

    private final DiscordBotService discordBot;

    public AdminDiscordConfigController(DiscordBotService discordBot) {
        this.discordBot = discordBot;
    }

    @GetMapping
    @Operation(summary = "Get the Discord bot's config",
            description = "Channels, roles, branding and application rules -- edited by the bot's /config command.")
    public ConfigView get() {
        return ConfigView.from(discordBot.getConfig());
    }

    @PatchMapping
    @Operation(summary = "Update the Discord bot's config", description = "Only non-null fields are changed.")
    public ConfigView update(@Valid @RequestBody UpdateConfigRequest request) {
        return ConfigView.from(discordBot.updateConfig(new DiscordBotService.Patch(
                request.applicationsChannelId(), request.welcomeChannelId(), request.serverInfoChannelId(),
                request.staffLogChannelId(), request.memberRoleId(), request.staffRoleIds(), request.minAge(),
                request.applyCooldownDays(), request.brandName(), request.brandColor(), request.brandIconUrl())));
    }

    public record UpdateConfigRequest(
            @Pattern(regexp = SNOWFLAKE, message = "not a Discord id") String applicationsChannelId,
            @Pattern(regexp = SNOWFLAKE, message = "not a Discord id") String welcomeChannelId,
            @Pattern(regexp = SNOWFLAKE, message = "not a Discord id") String serverInfoChannelId,
            @Pattern(regexp = SNOWFLAKE, message = "not a Discord id") String staffLogChannelId,
            @Pattern(regexp = SNOWFLAKE, message = "not a Discord id") String memberRoleId,
            List<@Pattern(regexp = SNOWFLAKE, message = "not a Discord id") String> staffRoleIds,
            @Min(1) @Max(120) Integer minAge,
            @Min(0) @Max(365) Integer applyCooldownDays,
            @Size(max = 100) String brandName,
            @Pattern(regexp = "#?[0-9A-Fa-f]{6}", message = "not a hex color") String brandColor,
            @Size(max = 300) String brandIconUrl) {}

    public record ConfigView(String applicationsChannelId, String welcomeChannelId, String serverInfoChannelId,
            String staffLogChannelId, String memberRoleId, List<String> staffRoleIds, int minAge,
            int applyCooldownDays, String brandName, String brandColor, String brandIconUrl) {
        static ConfigView from(DiscordConfig c) {
            return new ConfigView(c.getApplicationsChannelId(), c.getWelcomeChannelId(), c.getServerInfoChannelId(),
                    c.getStaffLogChannelId(), c.getMemberRoleId(), c.getStaffRoleIds(), c.getMinAge(),
                    c.getApplyCooldownDays(), c.getBrandName(), c.getBrandColor(), c.getBrandIconUrl());
        }
    }
}
