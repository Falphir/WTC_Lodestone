package io.github.falphir.hub.repository;

import io.github.falphir.hub.entity.BanSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BanSettingsRepository extends JpaRepository<BanSettings, Integer> {
}
