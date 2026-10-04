package io.github.falphir.hub.service;

import java.util.List;

import io.github.falphir.hub.entity.DiscordConfig;
import io.github.falphir.hub.entity.DiscordServer;
import io.github.falphir.hub.repository.DiscordConfigRepository;
import io.github.falphir.hub.repository.DiscordServerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Discord bot's runtime config and its display-only /serverinfo server list, both edited
 * through the bot's /config and /server commands. Replaces what used to be the bot's local
 * config.json/servers.json -- the hub is now the only place this state lives.
 */
@Service
public class DiscordBotService {

    private final DiscordConfigRepository configRepository;
    private final DiscordServerRepository serverRepository;

    public DiscordBotService(DiscordConfigRepository configRepository, DiscordServerRepository serverRepository) {
        this.configRepository = configRepository;
        this.serverRepository = serverRepository;
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

    @Transactional(readOnly = true)
    public List<DiscordServer> listServers() {
        return serverRepository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public DiscordServer addServer(String id, String name, String publicAddress, String modpack, String installUrl) {
        if (serverRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Server '" + id + "' already exists");
        }
        return serverRepository.save(new DiscordServer(id, name, publicAddress, modpack, installUrl));
    }

    @Transactional
    public DiscordServer updateServer(String id, ServerPatch patch) {
        DiscordServer server = serverRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such server '" + id + "'"));
        if (patch.name() != null) server.setName(patch.name());
        if (patch.publicAddress() != null) server.setPublicAddress(patch.publicAddress());
        if (patch.modpack() != null) server.setModpack(patch.modpack());
        if (patch.version() != null) server.setVersion(patch.version());
        if (patch.installUrl() != null) server.setInstallUrl(patch.installUrl());
        return serverRepository.save(server);
    }

    public record ServerPatch(String name, String publicAddress, String modpack, String version, String installUrl) {}

    @Transactional
    public void removeServer(String id) {
        if (!serverRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such server '" + id + "'");
        }
        serverRepository.deleteById(id);
    }
}
