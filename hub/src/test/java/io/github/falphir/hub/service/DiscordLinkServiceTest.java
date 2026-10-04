package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DiscordLinkServiceTest {

    @Autowired
    private DiscordLinkService service;

    @Test
    void setLinkUpsertsAndIsFindableByUuidAndName() {
        service.setLink("discord1", "0f3a1b2c-0000-4000-8000-000000000001", "Alice");

        assertThat(service.findLinkByDiscordId("discord1")).isPresent();
        assertThat(service.findLinkByUuid("0f3a1b2c-0000-4000-8000-000000000001")).isPresent();
        assertThat(service.findLinkByName("alice")).isPresent(); // case-insensitive
        assertThat(service.findLinkByName("bob")).isEmpty();

        // relink to a different account -- still one row for this discord id
        service.setLink("discord1", "0f3a1b2c-0000-4000-8000-000000000002", "Alice2");
        assertThat(service.listLinks()).hasSize(1);
        assertThat(service.findLinkByDiscordId("discord1").get().getMcUsername()).isEqualTo("Alice2");
    }

    @Test
    void removeLinkIsNoopWhenMissing() {
        service.removeLink("nobody");
        assertThat(service.findLinkByDiscordId("nobody")).isEmpty();
    }

    @Test
    void applicationLifecycle() {
        assertThat(service.getApplication("applicant1")).isEmpty();

        service.setApplicationPending("applicant1", "msg1", "uuid1", "Applicant");
        var pending = service.getApplication("applicant1").orElseThrow();
        assertThat(pending.getStatus()).isEqualTo("pending");
        assertThat(pending.getDecidedAt()).isNull();

        service.setApplicationDecision("applicant1", "denied");
        var decided = service.getApplication("applicant1").orElseThrow();
        assertThat(decided.getStatus()).isEqualTo("denied");
        assertThat(decided.getDecidedAt()).isNotNull();
        assertThat(decided.getCreatedAt()).isEqualTo(pending.getCreatedAt()); // preserved, not reset

        service.clearApplication("applicant1");
        assertThat(service.getApplication("applicant1")).isEmpty();
        service.clearApplication("applicant1"); // no-op, doesn't throw
    }

    @Test
    void decisionWithNoPriorApplicationStillSetsCreatedAt() {
        service.setApplicationDecision("applicant2", "approved");
        var app = service.getApplication("applicant2").orElseThrow();
        assertThat(app.getCreatedAt()).isGreaterThan(0);
        assertThat(app.getDecidedAt()).isEqualTo(app.getCreatedAt());
    }
}
