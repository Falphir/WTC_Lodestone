package io.github.falphir.hub.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.falphir.hub.entity.GameServer;
import io.github.falphir.hub.entity.ServerHeartbeat;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class ServerHeartbeatRepositoryTest {

    @Autowired
    private ServerHeartbeatRepository heartbeats;

    @Autowired
    private GameServerRepository servers;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deletesOnlyHeartbeatsOlderThanCutoff() {
        servers.save(new GameServer("retention-test", "Retention Test", "hash"));

        ServerHeartbeat old = heartbeats.save(new ServerHeartbeat("retention-test", 1, 10, 20.0, 100, 200));
        ServerHeartbeat recent = heartbeats.save(new ServerHeartbeat("retention-test", 2, 10, 20.0, 100, 200));
        entityManager.flush();

        entityManager.createQuery("update ServerHeartbeat h set h.recordedAt = :cutoff where h.id = :id")
                .setParameter("cutoff", Instant.now().minus(10, ChronoUnit.DAYS))
                .setParameter("id", old.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        int deleted = heartbeats.deleteByRecordedAtBefore(Instant.now().minus(7, ChronoUnit.DAYS));

        assertThat(deleted).isEqualTo(1);
        assertThat(heartbeats.findByServerIdOrderByRecordedAtDesc("retention-test"))
                .extracting(ServerHeartbeat::getId)
                .containsExactly(recent.getId());
    }
}
