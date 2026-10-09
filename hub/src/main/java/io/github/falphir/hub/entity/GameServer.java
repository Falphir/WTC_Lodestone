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

    /**
     * The server's own enforce-whitelist setting, from server.properties. Null until it reports.
     * False while {@link #syncWhitelist} is true means removals never actually kick anyone.
     */
    @Column(name = "enforce_whitelist")
    private Boolean enforceWhitelist;

    /** Version (content hash) of the network whitelist this server last applied. */
    @Column(name = "whitelist_version", length = 64)
    private String whitelistVersion;

    @Column(name = "whitelist_synced_at")
    private Instant whitelistSyncedAt;

    // ---- public listing: what the Discord bot's /serverinfo shows players, blank until filled in ----

    @Column(name = "public_address", nullable = false, length = 100)
    private String publicAddress = "";

    @Column(nullable = false, length = 100)
    private String modpack = "";

    @Column(name = "modpack_url", nullable = false, length = 300)
    private String modpackUrl = "";

    /** The modpack's own version, which is not any of the versions the mod reports. */
    @Column(name = "modpack_version", nullable = false, length = 32)
    private String modpackVersion = "";

    @Column(nullable = false, length = 32)
    private String launcher = "";

    /** The modpack's image, used as the thumbnail wherever the listing is shown. */
    @Column(name = "icon_url", nullable = false, length = 300)
    private String iconUrl = "";

    /** Whether players can see this server at all; nothing else here is public. */
    @Column(nullable = false)
    private boolean published = false;

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
    public void reportSetup(String modVersion, String minecraftVersion, String loaderVersion, Boolean syncWhitelist,
            Boolean enforceWhitelist) {
        this.modVersion = modVersion;
        this.minecraftVersion = minecraftVersion;
        this.loaderVersion = loaderVersion;
        this.syncWhitelist = syncWhitelist;
        this.enforceWhitelist = enforceWhitelist;
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
    public void setName(String name) { this.name = name; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeen() { return lastSeen; }
    public String getModVersion() { return modVersion; }
    public String getMinecraftVersion() { return minecraftVersion; }
    public String getLoaderVersion() { return loaderVersion; }
    public Boolean getSyncWhitelist() { return syncWhitelist; }
    public Boolean getEnforceWhitelist() { return enforceWhitelist; }
    public String getWhitelistVersion() { return whitelistVersion; }
    public Instant getWhitelistSyncedAt() { return whitelistSyncedAt; }

    public String getPublicAddress() { return publicAddress; }
    public void setPublicAddress(String publicAddress) { this.publicAddress = publicAddress; }
    public String getModpack() { return modpack; }
    public void setModpack(String modpack) { this.modpack = modpack; }
    public String getModpackUrl() { return modpackUrl; }
    public void setModpackUrl(String modpackUrl) { this.modpackUrl = modpackUrl; }
    public String getModpackVersion() { return modpackVersion; }
    public void setModpackVersion(String modpackVersion) { this.modpackVersion = modpackVersion; }
    public String getLauncher() { return launcher; }
    public void setLauncher(String launcher) { this.launcher = launcher; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
}