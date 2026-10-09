package io.github.falphir.hub.service;

import java.util.List;

import io.github.falphir.hub.entity.DiscordConfig;
import io.github.falphir.hub.repository.DiscordConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Discord bot's runtime config -- channels, roles, branding and application rules -- edited
 * through the bot's /config panel. Replaces what used to be the bot's local config.json; the hub
 * is now the only place this state lives. What /serverinfo shows players lives on
 * {@link io.github.falphir.hub.entity.GameServer} instead, as each server's public listing.
 */
@Service
public class DiscordBotService {

    private final DiscordConfigRepository configRepository;

    public DiscordBotService(DiscordConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Transactional(readOnly = true)
    public DiscordConfig getConfig() {
        return configRepository.findById(1).orElseGet(() -> new DiscordConfig(1));
    }

    /** Only non-null fields of the patch are changed, same merge semantics as the old setConfig(patch). */
    @Transactional
    public DiscordConfig updateConfig(Patch patch) {
        DiscordConfig config = configRepository.findById(1).orElseGet(() -> new DiscordConfig(1));
        if (patch.applicationsChannelId() != null) config.setApplicationsChannelId(patch.applicationsChannelId());
        if (patch.welcomeChannelId() != null) config.setWelcomeChannelId(patch.welcomeChannelId());
        if (patch.serverInfoChannelId() != null) config.setServerInfoChannelId(patch.serverInfoChannelId());
        if (patch.staffLogChannelId() != null) config.setStaffLogChannelId(patch.staffLogChannelId());
        if (patch.memberRoleId() != null) config.setMemberRoleId(patch.memberRoleId());
        if (patch.staffRoleIds() != null) config.setStaffRoleIds(patch.staffRoleIds());
        if (patch.minAge() != null) config.setMinAge(patch.minAge());
        if (patch.applyCooldownDays() != null) config.setApplyCooldownDays(patch.applyCooldownDays());
        if (patch.brandName() != null) config.setBrandName(patch.brandName());
        if (patch.brandColor() != null) config.setBrandColor(patch.brandColor());
        if (patch.brandIconUrl() != null) config.setBrandIconUrl(patch.brandIconUrl());
        return configRepository.save(config);
    }

    public record Patch(String applicationsChannelId, String welcomeChannelId, String serverInfoChannelId,
            String staffLogChannelId, String memberRoleId, List<String> staffRoleIds, Integer minAge,
            Integer applyCooldownDays, String brandName, String brandColor, String brandIconUrl) {}
}
