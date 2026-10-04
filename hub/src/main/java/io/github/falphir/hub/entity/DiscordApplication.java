package io.github.falphir.hub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An applicant's latest /apply, tracked so the bot can enforce one open application at a time and a re-apply cooldown. */
@Entity
@Table(name = "discord_applications")
public class DiscordApplication {

    @Id
    @Column(name = "discord_id", length = 32)
    private String discordId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "message_id", length = 32)
    private String messageId;

    @Column(name = "mc_uuid", length = 36)
    private String mcUuid;

    @Column(name = "mc_username", length = 16)
    private String mcUsername;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "decided_at")
    private Long decidedAt;

    protected DiscordApplication() {
        // required by JPA
    }

    public DiscordApplication(String discordId) {
        this.discordId = discordId;
    }

    public void markPending(String messageId, String uuid, String username) {
        this.status = "pending";
        this.messageId = messageId;
        this.mcUuid = uuid;
        this.mcUsername = username;
        this.createdAt = System.currentTimeMillis();
        this.decidedAt = null;
    }

    /** A decision can arrive with no prior pending row; createdAt then starts here too. */
    public void decide(String status) {
        long now = System.currentTimeMillis();
        if (this.createdAt == 0) this.createdAt = now;
        this.status = status;
        this.decidedAt = now;
    }

    public String getDiscordId() { return discordId; }
    public String getStatus() { return status; }
    public String getMessageId() { return messageId; }
    public String getMcUuid() { return mcUuid; }
    public String getMcUsername() { return mcUsername; }
    public long getCreatedAt() { return createdAt; }
    public Long getDecidedAt() { return decidedAt; }
}
