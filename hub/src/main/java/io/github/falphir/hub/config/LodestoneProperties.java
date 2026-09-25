package io.github.falphir.hub.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lodestone")
public record LodestoneProperties(List<Admin> admins) {

    public record Admin(String username, String password) {}
}
