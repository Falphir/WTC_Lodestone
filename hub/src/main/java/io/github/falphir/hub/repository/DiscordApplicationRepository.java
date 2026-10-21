package io.github.falphir.hub.repository;

import java.util.List;

import io.github.falphir.hub.entity.DiscordApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DiscordApplicationRepository extends JpaRepository<DiscordApplication, String> {

    /** Oldest first: the bot lists these for staff to pick from, and the longest wait comes first. */
    List<DiscordApplication> findByStatusOrderByCreatedAtAsc(String status);

    List<DiscordApplication> findAllByOrderByCreatedAtAsc();

    /** decidedAt is epoch millis. Pending rows have none, so they're never matched. */
    @Modifying
    @Query("delete from DiscordApplication a where a.status = :status and a.decidedAt < :cutoff")
    int deleteDecidedBefore(String status, long cutoff);
}
