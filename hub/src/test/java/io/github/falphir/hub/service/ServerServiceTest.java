package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.falphir.hub.entity.GameServer;
import io.github.falphir.hub.repository.GameServerRepository;
import io.github.falphir.hub.repository.ServerHeartbeatRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@Transactional
class ServerServiceTest {

    @Autowired
    private ServerService service;

    @Autowired
    private GameServerRepository servers;

    @Autowired
    private ServerHeartbeatRepository heartbeats;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void removeDeletesTheServerAndItsHeartbeats() {
        service.register("remove-test", "Remove Test", null);
        service.recordHeartbeat("remove-test", 1, 10, 20.0, 4.2, 100, 200, List.of());

        service.remove("remove-test");

        assertThat(servers.existsById("remove-test")).isFalse();
        assertThat(heartbeats.findByServerIdAndRecordedAtAfterOrderByRecordedAtDesc("remove-test", Instant.EPOCH)).isEmpty();
    }

    @Test
    void onlinePlayersComeFromTheLastHeartbeatAndStopBeingReportedOnceTheServerGoesQuiet() {
        service.register("presence-test", "Presence Test", null);
        service.recordHeartbeat("presence-test", 1, 10, 20.0, 4.2, 100, 200,
                List.of(new ServerService.OnlinePlayer("069a79f4-44e9-4726-a5be-fca90e38aaf5", "Notch")));

        assertThat(service.onlinePlayers(server("presence-test")))
                .extracting(ServerService.OnlinePlayer::name).containsExactly("Notch");

        // The snapshot is only as good as the heartbeat it came from: once the server has been quiet
        // long enough to count as offline, it must not still be reported as having players on.
        entityManager.createQuery("update GameServer s set s.lastSeen = :old where s.id = 'presence-test'")
                .setParameter("old", Instant.now().minus(service.offlineAfter()).minusSeconds(1))
                .executeUpdate();
        entityManager.clear();

        assertThat(service.onlinePlayers(server("presence-test"))).isEmpty();
    }

    private GameServer server(String id) {
        return servers.findById(id).orElseThrow();
    }

    @Test
    void removeThrowsForAnUnknownServer() {
        assertThatThrownBy(() -> service.remove("no-such-server")).isInstanceOf(ResponseStatusException.class);
    }

    /** Only the fields under test; everything else stays as it is. */
    private static ServerService.Patch patch(String id, String name) {
        return new ServerService.Patch(id, name, null, null, null, null, null, null, null);
    }

    /** A full public listing, as the register form or edit dialog submits one. */
    private static ServerService.Patch listing(Boolean published) {
        return new ServerService.Patch(null, null, "play.example.com", "ATM10", "https://example.com/pack",
                "0.7.1", "CurseForge", "https://example.com/icon.png", published);
    }

    @Test
    void registerStoresThePublicListingButLeavesItUnpublished() {
        service.register("register-test", "Register Test", listing(null));

        GameServer server = servers.findById("register-test").orElseThrow();
        assertThat(server.getPublicAddress()).isEqualTo("play.example.com");
        assertThat(server.getModpack()).isEqualTo("ATM10");
        assertThat(server.getModpackUrl()).isEqualTo("https://example.com/pack");
        assertThat(server.getModpackVersion()).isEqualTo("0.7.1");
        assertThat(server.getLauncher()).isEqualTo("CurseForge");
        assertThat(server.getIconUrl()).isEqualTo("https://example.com/icon.png");
        assertThat(server.isPublished()).isFalse();
    }

    @Test
    void editChangesTheDisplayName() {
        service.register("edit-test", "Old Name", null);

        service.edit("edit-test", patch(null, "New Name"));

        assertThat(servers.findById("edit-test").orElseThrow().getName()).isEqualTo("New Name");
    }

    @Test
    void editLeavesOutTheFieldsItIsNotGiven() {
        service.register("edit-test", "Edit Test", null);
        service.edit("edit-test", listing(true));

        service.edit("edit-test", patch(null, "Renamed"));

        GameServer server = servers.findById("edit-test").orElseThrow();
        assertThat(server.getName()).isEqualTo("Renamed");
        assertThat(server.getPublicAddress()).isEqualTo("play.example.com");
        assertThat(server.getModpackVersion()).isEqualTo("0.7.1");
        assertThat(server.getLauncher()).isEqualTo("CurseForge");
        assertThat(server.getIconUrl()).isEqualTo("https://example.com/icon.png");
        assertThat(server.isPublished()).isTrue();
    }

    @Test
    void editCanChangeTheIdAndCarriesTheRestOfTheRowAlong() {
        service.register("old-id", "Edit Test", null);
        service.recordHeartbeat("old-id", 1, 10, 20.0, 4.2, 100, 200, List.of());
        service.edit("old-id", listing(true));

        service.edit("old-id", patch("new-id", null));

        assertThat(servers.existsById("old-id")).isFalse();
        GameServer moved = servers.findById("new-id").orElseThrow();
        assertThat(moved.getName()).isEqualTo("Edit Test");
        assertThat(moved.getPublicAddress()).isEqualTo("play.example.com");
        assertThat(moved.isPublished()).isTrue();
        assertThat(heartbeats.findByServerIdAndRecordedAtAfterOrderByRecordedAtDesc("new-id", Instant.EPOCH)).hasSize(1);
    }

    @Test
    void editThrowsWhenTheNewIdIsAlreadyTaken() {
        service.register("edit-test", "Edit Test", null);
        service.register("taken", "Taken", null);

        assertThatThrownBy(() -> service.edit("edit-test", patch("taken", null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resetTokenIssuesANewTokenAndInvalidatesTheOld() {
        var registered = service.register("reset-test", "Reset Test", null);

        var reset = service.resetToken("reset-test");

        assertThat(reset.token()).isNotEqualTo(registered.token());
        assertThat(service.authenticate(registered.token())).isEmpty();
        assertThat(service.authenticate(reset.token())).isPresent();
    }
}
