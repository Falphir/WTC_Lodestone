package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

class ServerPowerServiceTest {

    private static final String PANEL_ID = "a4cc85f9";

    private final ServerService servers = mock(ServerService.class);
    private final PufferPanelClient panel = mock(PufferPanelClient.class);
    private final ServerPowerService power = new ServerPowerService(
            servers, panel, mock(ApplicationEventPublisher.class), Duration.ofMillis(10), Duration.ofMillis(300));

    @BeforeEach
    void linked() {
        when(panel.isConfigured()).thenReturn(true);
        when(servers.pufferpanelIdOf("atm10")).thenReturn(PANEL_ID);
    }

    @Test
    void stopIsSentToTheLinkedPanelServer() {
        power.act("atm10", "stop", "admin");

        verify(panel).power(PANEL_ID, "stop");
    }

    @Test
    void aRestartStartsOnceTheStopHasLanded() {
        when(panel.isRunning(PANEL_ID)).thenReturn(true, true, false);

        power.act("atm10", "restart", "admin");

        verify(panel).power(PANEL_ID, "stop");
        verify(panel, timeout(2000)).power(PANEL_ID, "start");
    }

    @Test
    void aServerThatNeverStopsIsNotStartedAgain() {
        when(panel.isRunning(PANEL_ID)).thenReturn(true);

        power.act("atm10", "restart", "admin");

        verify(panel, after(800).never()).power(PANEL_ID, "start");
    }

    @Test
    void aSecondRestartWhileOneIsUnderWayIsRefused() {
        when(panel.isRunning(PANEL_ID)).thenReturn(true);
        power.act("atm10", "restart", "admin");

        assertThatThrownBy(() -> power.act("atm10", "restart", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already restarting");
    }

    @Test
    void anUnknownActionIsRefusedBeforeAnythingIsSent() {
        assertThatThrownBy(() -> power.act("atm10", "explode", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown power action");
        verify(panel, never()).power(PANEL_ID, "explode");
    }
}
