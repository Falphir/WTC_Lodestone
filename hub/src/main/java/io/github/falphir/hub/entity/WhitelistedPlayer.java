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

    protected WhitelistedPlayer() {
        // required by JPA
    }

    public WhitelistedPlayer(String uuid, String name, String addedBy) {
        this.uuid = uuid;
        this.name = name;
        this.addedBy = addedBy;
        this.addedAt = Instant.now();
    }

    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public String getAddedBy() { return addedBy; }
    public Instant getAddedAt() { return addedAt; }
}
