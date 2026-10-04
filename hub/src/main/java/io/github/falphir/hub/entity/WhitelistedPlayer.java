package io.github.falphir.hub.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A player allowed on every server of the network. Keyed by Minecraft UUID, since names can change. */
@Entity
@Table(name = "whitelisted_players")
public class WhitelistedPlayer {

    /** Dashed lowercase UUID, as in whitelist.json. */
    @Id
    @Column(length = 36)
    private String uuid;

    @Column(nullable = false, length = 16)
    private String name;

    @Column(name = "added_by", nullable = false, length = 64)
    private String addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    /** Discord account this player is linked to via the bot's /link, if any. Unique when set. */
    @Column(name = "discord_id", length = 32)
    private String discordId;

    @Column(name = "discord_linked_at")
    private Long discordLinkedAt;

    protected WhitelistedPlayer() {
        // required by JPA
    }

    public WhitelistedPlayer(String uuid, String name, String addedBy) {
        this.uuid = uuid;
        this.name = name;
        this.addedBy = addedBy;
        this.addedAt = Instant.now();
    }

    /** Also refreshes `name` to the canonical casing Mojang just resolved. */
    public void linkDiscord(String discordId, String canonicalName) {
        this.discordId = discordId;
        this.discordLinkedAt = System.currentTimeMillis();
        this.name = canonicalName;
    }

    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public String getAddedBy() { return addedBy; }
    public Instant getAddedAt() { return addedAt; }
    public String getDiscordId() { return discordId; }
    public Long getDiscordLinkedAt() { return discordLinkedAt; }
}
