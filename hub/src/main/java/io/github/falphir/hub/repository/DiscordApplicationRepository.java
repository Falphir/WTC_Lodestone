package io.github.falphir.hub.repository;

import io.github.falphir.hub.entity.DiscordApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscordApplicationRepository extends JpaRepository<DiscordApplication, String> {
}
