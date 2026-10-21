package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.falphir.hub.entity.DiscordApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DiscordApplicationServiceTest {

    @Autowired
    private DiscordApplicationService service;

    @Test
    void listsOnlyTheRequestedStatusOldestFirst() {
        // This hub's table is shared with whoever else is using it, so assert on the rows this
        // test created rather than on the whole listing.
        service.setApplicationPending("test-app-older", "m1");
        service.setApplicationPending("test-app-newer", "m2");
        service.setApplicationPending("test-app-decided", "m3");
        service.setApplicationDecision("test-app-decided", "denied");

        var pending = service.listApplications("pending").stream().map(DiscordApplication::getDiscordId).toList();
        assertThat(pending).contains("test-app-older", "test-app-newer").doesNotContain("test-app-decided");
        assertThat(pending.indexOf("test-app-older")).isLessThan(pending.indexOf("test-app-newer"));

        var denied = service.listApplications("denied").stream().map(DiscordApplication::getDiscordId).toList();
        assertThat(denied).contains("test-app-decided").doesNotContain("test-app-older");

        // No status means every status
        var all = service.listApplications(null).stream().map(DiscordApplication::getDiscordId).toList();
        assertThat(all).contains("test-app-older", "test-app-newer", "test-app-decided");
    }
}
