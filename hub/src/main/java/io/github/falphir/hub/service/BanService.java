package io.github.falphir.hub.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.falphir.hub.entity.BanSettings;
import io.github.falphir.hub.entity.NetworkBan;
import io.github.falphir.hub.repository.BanSettingsRepository;
import io.github.falphir.hub.repository.NetworkBanRepository;

/**
 * The network-wide ban list. Unlike the whitelist there's no "never populated" guard: servers only
 * add and remove the bans they got from here, so an empty list never touches a server's own bans.
 */
@Service
public class BanService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BanService.class);

    /** What network-banned players see until an admin writes their own. &-codes are colors; the mod fills in the placeholders. */
    public static final String DEFAULT_MESSAGE =
            "&cYou are banned from this network.\n\n&7Reason: &f{reason}\n&7Expires: &f{expires}";

    private final NetworkBanRepository bans;
    private final BanSettingsRepository settings;
    private final MojangProfiles mojang;
    private final TokenService hashing;
    private final ApplicationEventPublisher events;

    public BanService(NetworkBanRepository bans, BanSettingsRepository settings, MojangProfiles mojang, TokenService hashing,
            ApplicationEventPublisher events) {
        this.settings = settings;
        this.bans = bans;
        this.mojang = mojang;
        this.hashing = hashing;
        this.events = events;
    }

    /** The message network-banned players get: the admin's, or {@link #DEFAULT_MESSAGE} when none is set. */
    @Transactional(readOnly = true)
    public String message() {
        return settings.findById(1).map(BanSettings::getMessage).orElse(DEFAULT_MESSAGE);
    }

    /** Blank goes back to the default. */
    @Transactional
    public void setMessage(String message) {
        BanSettings row = settings.findById(1).orElseGet(() -> new BanSettings(1));
        row.setMessage(message == null || message.isBlank() ? null : message.strip());
        settings.save(row);
        events.publishEvent(HubEvent.bans());
    }

    /** Every ban, expired ones included until the nightly cleanup removes them. */
    @Transactional(readOnly = true)
    public List<NetworkBan> list() {
        return bans.findAllByOrderByNameAsc();
    }

    /** What servers should be enforcing right now. */
    @Transactional(readOnly = true)
    public List<NetworkBan> active() {
        return bans.findActive(Instant.now());
    }

    @Transactional(readOnly = true)
    public String version() {
        return version(active());
    }

    /**
     * Identifies what servers should have applied. Reason and expiry are part of it, not just who:
     * changing either is a change every server has to pick up.
     */
    public String version(List<NetworkBan> active) {
        return hashing.hash(active.stream()
                .map(b -> b.getUuid() + "|" + Objects.toString(b.getExpiresAt(), "") + "|" + Objects.toString(b.getReason(), ""))
                .sorted()
                .collect(Collectors.joining(",")));
    }

    /** Bans the Minecraft account with this name, looked up through Mojang. Banning again replaces the ban. */
    @Transactional
    public NetworkBan ban(String name, String reason, Instant expiresAt, String bannedBy) {
        MojangProfiles.Profile profile = mojang.lookup(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No Minecraft account named '" + name + "'"));
        NetworkBan ban = bans.findById(profile.uuid()).orElseGet(() -> new NetworkBan(profile.uuid(), profile.name()));
        ban.ban(profile.name(), reason, expiresAt, bannedBy);
        NetworkBan saved = bans.save(ban);
        events.publishEvent(HubEvent.bans());
        return saved;
    }

    @Transactional
    public void unban(String uuid) {
        if (!bans.existsById(uuid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player is not banned");
        }
        bans.deleteById(uuid);
        events.publishEvent(HubEvent.bans());
    }

    /** An expired ban has done its job; servers have already let the player back in. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void deleteExpired() {
        int deleted = bans.deleteExpired(Instant.now());
        if (deleted > 0) {
            LOGGER.info("Deleted {} expired ban(s)", deleted);
            events.publishEvent(HubEvent.bans());
        }
    }
}
