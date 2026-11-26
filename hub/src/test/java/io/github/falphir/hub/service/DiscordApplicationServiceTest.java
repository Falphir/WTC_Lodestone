package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import io.github.falphir.hub.entity.DiscordApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class DiscordApplicationServiceTest {

    @Autowired
    private DiscordApplicationService service;

    @Test
    void listsOnlyTheRequestedStatusOldestFirst() {
        // This hub's table is shared with whoever else is using it, so assert on the rows this
        // test created rather than on the whole listing.
        service.setApplicationPending("test-app-older", "m1", null, null);
        service.setApplicationPending("test-app-newer", "m2", null, null);
        service.setApplicationPending("test-app-decided", "m3", null, null);
        service.setApplicationDecision("test-app-decided", "denied", null, true);

        var pending = service.listApplications("pending").stream().map(DiscordApplication::getDiscordId).toList();
        assertThat(pending).contains("test-app-older", "test-app-newer").doesNotContain("test-app-decided");
        assertThat(pending.indexOf("test-app-older")).isLessThan(pending.indexOf("test-app-newer"));

        var denied = service.listApplications("denied").stream().map(DiscordApplication::getDiscordId).toList();
        assertThat(denied).contains("test-app-decided").doesNotContain("test-app-older");

        // No status means every status
        var all = service.listApplications(null).stream().map(DiscordApplication::getDiscordId).toList();
        assertThat(all).contains("test-app-older", "test-app-newer", "test-app-decided");
    }

    @Test
    void keepsTheAnswersAsJson() {
        var app = service.setApplicationPending("test-app-answers", "m", "alice", Map.of("mc", "Alice", "age", "19"));

        assertThat(app.getDiscordName()).isEqualTo("alice");
        assertThat(app.getAnswers()).contains("\"mc\":\"Alice\"", "\"age\":\"19\"");
    }

    @Test
    void refusesADecisionOnAnApplicationThatIsNotPending() {
        assertThatThrownBy(() -> service.setApplicationDecision("test-app-missing", "approved", null, true))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        service.setApplicationPending("test-app-twice", "m", null, null);
        service.setApplicationDecision("test-app-twice", "approved", null, true);
        assertThatThrownBy(() -> service.setApplicationDecision("test-app-twice", "denied", "late", false))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void keepsAReasonOnlyOnADenial() {
        service.setApplicationPending("test-app-deny", "m", null, null);
        service.setApplicationPending("test-app-approve", "m", null, null);

        assertThat(service.setApplicationDecision("test-app-deny", "denied", "  too short  ", true).getDenyReason())
                .isEqualTo("too short");
        assertThat(service.setApplicationDecision("test-app-approve", "approved", "ignored", true).getDenyReason())
                .isNull();
    }

    @Test
    void aDecisionTheBotDidNotMakeWaitsForTheBot() {
        service.setApplicationPending("test-app-dashboard", "m", null, null);
        service.setApplicationPending("test-app-review", "m", null, null);
        service.setApplicationDecision("test-app-dashboard", "approved", null, false);
        service.setApplicationDecision("test-app-review", "approved", null, true);

        var waiting = service.listUnapplied().stream().map(DiscordApplication::getDiscordId).toList();
        assertThat(waiting).contains("test-app-dashboard").doesNotContain("test-app-review");

        service.markApplied("test-app-dashboard");
        service.markApplied("test-app-missing"); // a no-op, not an error

        assertThat(service.listUnapplied()).extracting(DiscordApplication::getDiscordId)
                .doesNotContain("test-app-dashboard");
    }
}
