package io.github.falphir.hub.repository;

import io.github.falphir.hub.entity.DiscordConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscordConfigRepository extends JpaRepository<DiscordConfig, Integer> {
}
