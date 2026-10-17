package io.github.falphir.hub.repository;

import java.util.List;

import io.github.falphir.hub.entity.DiscordApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscordApplicationRepository extends JpaRepository<DiscordApplication, String> {

    /** Oldest first: the bot lists these for staff to pick from, and the longest wait comes first. */
    List<DiscordApplication> findByStatusOrderByCreatedAtAsc(String status);

    List<DiscordApplication> findAllByOrderByCreatedAtAsc();
}
