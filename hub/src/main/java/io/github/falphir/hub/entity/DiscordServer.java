package io.github.falphir.hub.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Display-only entry for the Discord bot's /serverinfo -- no auth token, no bridge access. */
@Entity
@Table(name = "discord_servers")
public class DiscordServer {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "public_address", nullable = false, length = 100)
    private String publicAddress = "";

    @Column(nullable = false, length = 100)
    private String modpack = "";

    @Column(nullable = false, length = 32)
    private String version = "";

    @Column(name = "install_url", nullable = false, length = 300)
    private String installUrl = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DiscordServer() {
        // required by JPA
    }

    public DiscordServer(String id, String name, String publicAddress, String modpack, String installUrl) {
        this.id = id;
        this.name = name;
        this.publicAddress = publicAddress == null ? "" : publicAddress;
        this.modpack = modpack == null ? "" : modpack;
        this.installUrl = installUrl == null ? "" : installUrl;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPublicAddress() { return publicAddress; }
    public void setPublicAddress(String publicAddress) { this.publicAddress = publicAddress; }
    public String getModpack() { return modpack; }
    public void setModpack(String modpack) { this.modpack = modpack; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getInstallUrl() { return installUrl; }
    public void setInstallUrl(String installUrl) { this.installUrl = installUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
