package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DiscordBotServiceTest {

    @Autowired
    private DiscordBotService service;

    @Test
    void updateConfigOnlyChangesNonNullFields() {
        // Every value this asserts is one this test set: the row is shared with whoever else uses
        // this hub, so a real /config edit would otherwise decide whether the assertions hold.
        service.updateConfig(new DiscordBotService.Patch(
                "1", null, null, null, null, List.of("10", "20"), 21, 7, "WTC", null, null));

        var config = service.getConfig();
        assertThat(config.getApplicationsChannelId()).isEqualTo("1");
        assertThat(config.getStaffRoleIds()).containsExactly("10", "20");
        assertThat(config.getMinAge()).isEqualTo(21);
        assertThat(config.getBrandName()).isEqualTo("WTC");

        service.updateConfig(new DiscordBotService.Patch(
                null, "2", null, null, null, null, null, null, null, null, null));

        var after = service.getConfig();
        assertThat(after.getWelcomeChannelId()).isEqualTo("2");
        assertThat(after.getApplicationsChannelId()).isEqualTo("1"); // still untouched
        assertThat(after.getApplyCooldownDays()).isEqualTo(7);
        assertThat(after.getMinAge()).isEqualTo(21);
    }
}
