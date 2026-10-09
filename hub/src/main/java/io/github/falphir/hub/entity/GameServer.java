package io.github.falphir.hub.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "servers")
public class GameServer {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "mod_version", length = 32)
    private String modVersion;

    @Column(name = "minecraft_version", length = 32)
    private String minecraftVersion;

    @Column(name = "loader_version", length = 32)
    private String loaderVersion;

    /** Whether the server mirrors the network whitelist (its syncWhitelist config); null until it reports. */
    @Column(name = "sync_whitelist")
    private Boolean syncWhitelist;

    /** Version (content hash) of the network whitelist this server last applied. */
    @Column(name = "whitelist_version", length = 64)
    private String whitelistVersion;

    @Column(name = "whitelist_synced_at")
    private Instant whitelistSyncedAt;

    protected GameServer() {
        // required by JPA
    }

    public GameServer(String id, String name, String tokenHash) {
        this.id = id;
        this.name = name;
        this.tokenHash = tokenHash;
        this.createdAt = Instant.now();
    }

    public void markSeen() {
        this.lastSeen = Instant.now();
    }

    /** Stores what the mod reported about itself on check-in. */
    public void reportSetup(String modVersion, String minecraftVersion, String loaderVersion, Boolean syncWhitelist) {
        this.modVersion = modVersion;
        this.minecraftVersion = minecraftVersion;
        this.loaderVersion = loaderVersion;
        this.syncWhitelist = syncWhitelist;
    }

    public void whitelistApplied(String version) {
        this.whitelistVersion = version;
        this.whitelistSyncedAt = Instant.now();
    }

    /** Swaps the stored token hash; the old token stops authenticating immediately. */
    public void rotateToken(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeen() { return lastSeen; }
    public String getModVersion() { return modVersion; }
    public String getMinecraftVersion() { return minecraftVersion; }
    public String getLoaderVersion() { return loaderVersion; }
    public Boolean getSyncWhitelist() { return syncWhitelist; }
    public String getWhitelistVersion() { return whitelistVersion; }
    public Instant getWhitelistSyncedAt() { return whitelistSyncedAt; }
}