package io.github.falphir.hub.repository;

import java.util.Optional;

import io.github.falphir.hub.entity.DiscordLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscordLinkRepository extends JpaRepository<DiscordLink, String> {
    Optional<DiscordLink> findByMcUuid(String mcUuid);
    Optional<DiscordLink> findByMcUsernameIgnoreCase(String mcUsername);
}
