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

    @Test
    void editChangesTheDisplayName() {
        service.register("edit-test", "Old Name");

        service.edit("edit-test", "New Name", "edit-test");

        assertThat(servers.findById("edit-test").orElseThrow().getName()).isEqualTo("New Name");
    }

    @Test
    void editCanChangeTheIdAndCarriesItsHeartbeatsAlong() {
        service.register("old-id", "Edit Test");
        service.recordHeartbeat("old-id", 1, 10, 20.0, 100, 200);

        service.edit("old-id", "Edit Test", "new-id");

        assertThat(servers.existsById("old-id")).isFalse();
        assertThat(servers.findById("new-id").orElseThrow().getName()).isEqualTo("Edit Test");
        assertThat(heartbeats.findByServerIdAndRecordedAtAfterOrderByRecordedAtDesc("new-id", Instant.EPOCH)).hasSize(1);
    }

    @Test
    void editThrowsWhenTheNewIdIsAlreadyTaken() {
        service.register("edit-test", "Edit Test");
        service.register("taken", "Taken");

        assertThatThrownBy(() -> service.edit("edit-test", "Edit Test", "taken")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resetTokenIssuesANewTokenAndInvalidatesTheOld() {
        var registered = service.register("reset-test", "Reset Test");

        var reset = service.resetToken("reset-test");

        assertThat(reset.token()).isNotEqualTo(registered.token());
        assertThat(service.authenticate(registered.token())).isEmpty();
        assertThat(service.authenticate(reset.token())).isPresent();
    }
}
