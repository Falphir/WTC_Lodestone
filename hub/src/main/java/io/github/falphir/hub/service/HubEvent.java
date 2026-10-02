package io.github.falphir.hub.service;

/**
 * Something changed that open dashboards should reload. Published by the services, pushed to
 * dashboards by {@link io.github.falphir.hub.config.LiveUpdates}.
 *
 * @param topic    "servers", "whitelist" or "admins"
 * @param serverId the server it's about, or null
 */
public record HubEvent(String topic, String serverId) {

    public static HubEvent servers(String serverId) { return new HubEvent("servers", serverId); }
    public static HubEvent whitelist() { return new HubEvent("whitelist", null); }
    public static HubEvent admins() { return new HubEvent("admins", null); }
}
