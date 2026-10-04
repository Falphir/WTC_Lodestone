package io.github.falphir.hub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One MC account per Discord account (PK) and vice-versa (unique mcUuid) -- the Discord bot's /link. */
@Entity
@Table(name = "discord_links")
public class DiscordLink {

    @Id
    @Column(name = "discord_id", length = 32)
    private String discordId;

    @Column(name = "mc_uuid", nullable = false, unique = true, length = 36)
    private String mcUuid;

    @Column(name = "mc_username", nullable = false, length = 16)
    private String mcUsername;

    @Column(name = "linked_at", nullable = false)
    private long linkedAt;

    protected DiscordLink() {
        // required by JPA
    }

    public DiscordLink(String discordId) {
        this.discordId = discordId;
    }

    /** (Re)points this Discord account at `uuid`/`username`, e.g. on first link or a rename/switch. */
    public void relink(String uuid, String username) {
        this.mcUuid = uuid;
        this.mcUsername = username;
        this.linkedAt = System.currentTimeMillis();
    }

    public String getDiscordId() { return discordId; }
    public String getMcUuid() { return mcUuid; }
    public String getMcUsername() { return mcUsername; }
    public long getLinkedAt() { return linkedAt; }
}
