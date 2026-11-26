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

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "decided_at")
    private Long decidedAt;

    @Column(name = "discord_name", length = 64)
    private String discordName;

    /** The /apply form as a JSON object, for the dashboard; null on applications from before it was kept. */
    @Column(length = 4000)
    private String answers;

    @Column(name = "deny_reason", length = 500)
    private String denyReason;

    /** Decided off Discord, and the bot hasn't granted the role / sent the DM yet. */
    @Column(name = "bot_pending", nullable = false)
    private boolean botPending;

    protected DiscordApplication() {
        // required by JPA
    }

    public DiscordApplication(String discordId) {
        this.discordId = discordId;
    }

    public void markPending(String messageId, String discordName, String answers) {
        this.status = "pending";
        this.messageId = messageId;
        this.discordName = discordName;
        this.answers = answers;
        this.createdAt = System.currentTimeMillis();
        this.decidedAt = null;
        this.denyReason = null;
        this.botPending = false;
    }

    public void decide(String status, String denyReason, boolean botPending) {
        this.status = status;
        this.decidedAt = System.currentTimeMillis();
        this.denyReason = denyReason;
        this.botPending = botPending;
    }

    public void markApplied() {
        this.botPending = false;
    }

    public String getDiscordId() { return discordId; }
    public String getStatus() { return status; }
    public String getMessageId() { return messageId; }
    public long getCreatedAt() { return createdAt; }
    public Long getDecidedAt() { return decidedAt; }
    public String getDiscordName() { return discordName; }
    public String getAnswers() { return answers; }
    public String getDenyReason() { return denyReason; }
    public boolean isBotPending() { return botPending; }
}
