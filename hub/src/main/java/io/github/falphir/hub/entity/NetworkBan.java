package io.github.falphir.hub.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A player banned on every server. Separate from the whitelist: unbanning (or expiry) restores access as it was. */
@Entity
@Table(name = "network_bans")
public class NetworkBan {

    @Id
    @Column(length = 36)
    private String uuid;

    @Column(nullable = false, length = 16)
    private String name;

    @Column(length = 255)
    private String reason;

    @Column(name = "banned_by", nullable = false, length = 64)
    private String bannedBy;

    @Column(name = "banned_at", nullable = false)
    private Instant bannedAt;

    /** Null is permanent. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    protected NetworkBan() {
        // required by JPA
    }

    public NetworkBan(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    /** Banning someone already banned replaces the ban: new reason, new expiry, new "banned by". */
    public void ban(String name, String reason, Instant expiresAt, String bannedBy) {
        this.name = name;
        this.reason = reason == null || reason.isBlank() ? null : reason.strip();
        this.expiresAt = expiresAt;
        this.bannedBy = bannedBy;
        this.bannedAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public String getReason() { return reason; }
    public String getBannedBy() { return bannedBy; }
    public Instant getBannedAt() { return bannedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
