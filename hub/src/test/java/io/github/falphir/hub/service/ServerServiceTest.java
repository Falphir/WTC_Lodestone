package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.falphir.hub.repository.GameServerRepository;
import io.github.falphir.hub.repository.ServerHeartbeatRepository;

@SpringBootTest
@Transactional
class ServerServiceTest {

    @Autowired
    private ServerService service;

    @Autowired
    private GameServerRepository servers;

    @Autowired
    private ServerHeartbeatRepository heartbeats;

    @Test
    void removeDeletesTheServerAndItsHeartbeats() {
        service.register("remove-test", "Remove Test");
        service.recordHeartbeat("remove-test", 1, 10, 20.0, 100, 200);

        service.remove("remove-test");

        assertThat(servers.existsById("remove-test")).isFalse();
        assertThat(heartbeats.findByServerIdAndRecordedAtAfterOrderByRecordedAtDesc("remove-test", Instant.EPOCH)).isEmpty();
    }

    @Test
    void removeThrowsForAnUnknownServer() {
        assertThatThrownBy(() -> service.remove("no-such-server")).isInstanceOf(ResponseStatusException.class);
    }
}
