package io.github.falphir.hub.entity;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Singleton row (id always 1): the Discord bot's runtime-editable settings, set via /config. */
@Entity
@Table(name = "discord_config")
public class DiscordConfig {

    @Id
    private int id;

    @Column(name = "applications_channel_id", length = 32)
    private String applicationsChannelId;

    @Column(name = "welcome_channel_id", length = 32)
    private String welcomeChannelId;

    @Column(name = "server_info_channel_id", length = 32)
    private String serverInfoChannelId;

    @Column(name = "staff_log_channel_id", length = 32)
    private String staffLogChannelId;

    @Column(name = "member_role_id", length = 32)
    private String memberRoleId;

    // Comma-joined role ids; see getStaffRoleIds/setStaffRoleIds.
    @Column(name = "staff_role_ids", nullable = false, length = 1000)
    private String staffRoleIds = "";

    @Column(name = "min_age", nullable = false)
    private int minAge = 18;

    @Column(name = "apply_cooldown_days", nullable = false)
    private int applyCooldownDays = 7;

    @Column(name = "brand_name", nullable = false, length = 100)
    private String brandName = "The Community";

    @Column(name = "brand_color", nullable = false, length = 7)
    private String brandColor = "#5865f2";

    @Column(name = "brand_icon_url", length = 300)
    private String brandIconUrl;

    protected DiscordConfig() {
        // required by JPA
    }

    public DiscordConfig(int id) {
        this.id = id;
    }

    public String getApplicationsChannelId() { return applicationsChannelId; }
    public void setApplicationsChannelId(String v) { this.applicationsChannelId = v; }
    public String getWelcomeChannelId() { return welcomeChannelId; }
    public void setWelcomeChannelId(String v) { this.welcomeChannelId = v; }
    public String getServerInfoChannelId() { return serverInfoChannelId; }
    public void setServerInfoChannelId(String v) { this.serverInfoChannelId = v; }
    public String getStaffLogChannelId() { return staffLogChannelId; }
    public void setStaffLogChannelId(String v) { this.staffLogChannelId = v; }
    public String getMemberRoleId() { return memberRoleId; }
    public void setMemberRoleId(String v) { this.memberRoleId = v; }

    public List<String> getStaffRoleIds() {
        return staffRoleIds.isBlank() ? List.of() : Arrays.asList(staffRoleIds.split(","));
    }

    public void setStaffRoleIds(List<String> ids) {
        this.staffRoleIds = ids == null || ids.isEmpty() ? "" : String.join(",", ids);
    }

    public int getMinAge() { return minAge; }
    public void setMinAge(int v) { this.minAge = v; }
    public int getApplyCooldownDays() { return applyCooldownDays; }
    public void setApplyCooldownDays(int v) { this.applyCooldownDays = v; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String v) { this.brandName = v; }
    public String getBrandColor() { return brandColor; }
    public void setBrandColor(String v) { this.brandColor = v; }
    public String getBrandIconUrl() { return brandIconUrl; }
    public void setBrandIconUrl(String v) { this.brandIconUrl = v; }
}
