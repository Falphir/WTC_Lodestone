package io.github.falphir.hub.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lodestone")
public record LodestoneProperties(List<Admin> admins, String jwtSecret, PufferPanel pufferpanel) {

    public record Admin(String username, String password) {}

    /**
     * Where the hub reaches PufferPanel, and the OAuth2 client it signs in as (created in the panel
     * under an admin account's settings). Optional: leave any of it blank and nothing calls out.
     */
    public record PufferPanel(String url, String clientId, String clientSecret) {
        public boolean isConfigured() {
            return url != null && !url.isBlank() && clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }
    }
}
