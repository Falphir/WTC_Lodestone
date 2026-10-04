package io.github.falphir.hub.service;

import java.util.List;
import java.util.Optional;

import io.github.falphir.hub.entity.DiscordApplication;
import io.github.falphir.hub.entity.DiscordLink;
import io.github.falphir.hub.repository.DiscordApplicationRepository;
import io.github.falphir.hub.repository.DiscordLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Discord bot's Discord&lt;-&gt;Minecraft account links and in-flight applications --
 * replaces what used to be the bot's local SQLite (links.db). The hub is now the only place
 * this state lives, same as its config and server list (see DiscordBotService).
 */
@Service
public class DiscordLinkService {

    private final DiscordLinkRepository links;
    private final DiscordApplicationRepository applications;

    public DiscordLinkService(DiscordLinkRepository links, DiscordApplicationRepository applications) {
        this.links = links;
        this.applications = applications;
    }

    @Transactional(readOnly = true)
    public List<DiscordLink> listLinks() {
        return links.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<DiscordLink> findLinkByDiscordId(String discordId) {
        return links.findById(discordId);
    }

    @Transactional(readOnly = true)
    public Optional<DiscordLink> findLinkByUuid(String uuid) {
        return links.findByMcUuid(uuid);
    }

    @Transactional(readOnly = true)
    public Optional<DiscordLink> findLinkByName(String name) {
        return links.findByMcUsernameIgnoreCase(name);
    }

    /** One MC account per Discord account -- upserts, overwriting any prior link for this Discord id. */
    @Transactional
    public DiscordLink setLink(String discordId, String uuid, String username) {
        DiscordLink link = links.findById(discordId).orElseGet(() -> new DiscordLink(discordId));
        link.relink(uuid, username);
        return links.save(link);
    }

    /** A no-op if there wasn't one, same as the SQLite DELETE it replaces. */
    @Transactional
    public void removeLink(String discordId) {
        if (links.existsById(discordId)) links.deleteById(discordId);
    }

    @Transactional(readOnly = true)
    public Optional<DiscordApplication> getApplication(String discordId) {
        return applications.findById(discordId);
    }

    @Transactional
    public DiscordApplication setApplicationPending(String discordId, String messageId, String uuid, String username) {
        DiscordApplication app = applications.findById(discordId).orElseGet(() -> new DiscordApplication(discordId));
        app.markPending(messageId, uuid, username);
        return applications.save(app);
    }

    @Transactional
    public DiscordApplication setApplicationDecision(String discordId, String status) {
        DiscordApplication app = applications.findById(discordId).orElseGet(() -> new DiscordApplication(discordId));
        app.decide(status);
        return applications.save(app);
    }

    /** A no-op if there wasn't one, same as the SQLite DELETE it replaces. */
    @Transactional
    public void clearApplication(String discordId) {
        if (applications.existsById(discordId)) applications.deleteById(discordId);
    }
}
