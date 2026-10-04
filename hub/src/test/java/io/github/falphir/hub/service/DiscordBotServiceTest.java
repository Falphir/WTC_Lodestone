package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class DiscordBotServiceTest {

    @Autowired
    private DiscordBotService service;

    @Test
    void updateConfigOnlyChangesNonNullFields() {
        service.updateConfig(new DiscordBotService.Patch(
                "1", null, null, null, null, List.of("10", "20"), 21, null, "WTC", null, null));

        var config = service.getConfig();
        assertThat(config.getApplicationsChannelId()).isEqualTo("1");
        assertThat(config.getStaffRoleIds()).containsExactly("10", "20");
        assertThat(config.getMinAge()).isEqualTo(21);
        assertThat(config.getBrandName()).isEqualTo("WTC");
        assertThat(config.getApplyCooldownDays()).isEqualTo(7); // untouched default

        service.updateConfig(new DiscordBotService.Patch(
                null, "2", null, null, null, null, null, null, null, null, null));
        assertThat(service.getConfig().getApplicationsChannelId()).isEqualTo("1"); // still untouched
        assertThat(service.getConfig().getWelcomeChannelId()).isEqualTo("2");
    }

    @Test
    void addServerRefusesDuplicateId() {
        service.addServer("packa", "Pack A", "play.example.com", "ATM10", "https://example.com");
        assertThatThrownBy(() -> service.addServer("packa", "Pack A again", "", "", ""))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateAndRemoveServer() {
        service.addServer("packb", "Pack B", "play.example.com:2", "Create", "");
        var updated = service.updateServer("packb", new DiscordBotService.ServerPatch("Pack B Tech", null, null, "1.2", null));
        assertThat(updated.getName()).isEqualTo("Pack B Tech");
        assertThat(updated.getPublicAddress()).isEqualTo("play.example.com:2"); // untouched

        service.removeServer("packb");
        assertThat(service.listServers()).extracting("id").doesNotContain("packb");
        assertThatThrownBy(() -> service.removeServer("packb")).isInstanceOf(ResponseStatusException.class);
    }
}
