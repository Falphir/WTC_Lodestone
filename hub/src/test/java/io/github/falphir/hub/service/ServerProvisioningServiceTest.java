package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.falphir.hub.repository.GameServerRepository;

/** One-step server creation, with PufferPanel and the GitHub release mocked and the hub's own side real. */
@SpringBootTest
@Transactional
class ServerProvisioningServiceTest {

    private static final String PANEL_ID = "prov1234";

    @Autowired
    private ServerProvisioningService provisioning;

    @Autowired
    private GameServerRepository servers;

    @MockitoBean
    private PufferPanelClient panel;

    @MockitoBean
    private ModRelease modRelease;

    @BeforeEach
    void panelWithANeoForgeTemplate() {
        when(panel.isConfigured()).thenReturn(true);
        when(panel.serverHubUrl()).thenReturn("http://127.0.0.1:8081");
        when(panel.template("neoforge")).thenReturn(Map.of(
                "name", "neoforge",
                "display", "NeoForge",
                "readme", "notes",
                "data", Map.of(
                        "memory", Map.of("type", "integer", "value", 2048, "display", "Memory (MB)"),
                        "port", Map.of("type", "integer", "value", 25565),
                        "secret", Map.of("type", "string", "value", "x", "internal", true)),
                "supportedEnvironments", List.of(Map.of("type", "standard"))));
        when(panel.nodes()).thenReturn(List.of(Map.of("id", 0, "name", "LocalNode")));
        when(panel.createServer(any())).thenReturn(PANEL_ID);
        // registering links it, and linking checks the panel has it
        when(panel.listServers()).thenReturn(List.of(new PufferPanelClient.PanelServer(PANEL_ID, "Provisioned", 0, null, 25570, "minecraft-java")));
        when(modRelease.latestNeoForgeJar()).thenReturn(new ModRelease.Jar("wtc_lodestone-neoforge-1.21.1-1.0.0.jar", new byte[] {1, 2, 3}));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsTheServerRegistersItAndWritesItsModConfigAndJar() {
        var created = provisioning.create(new ServerProvisioningService.CreateRequest(
                "neoforge", null, "prov-test", "Provisioned", Map.of("memory", 4096, "port", 25570)), "admin");

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(panel).createServer(body.capture());
        Map<String, Object> data = (Map<String, Object>) body.getValue().get("data");
        assertThat(((Map<String, Object>) data.get("memory")).get("value")).isEqualTo(4096);
        assertThat(body.getValue()).containsEntry("name", "Provisioned").containsEntry("node", 0).doesNotContainKey("readme");
        assertThat(body.getValue().get("environment")).isEqualTo(Map.of("type", "standard"));

        assertThat(servers.findById("prov-test")).get().extracting(s -> s.getPufferpanelId()).isEqualTo(PANEL_ID);
        assertThat(created.token()).isNotBlank();
        assertThat(created.warnings()).isEmpty();

        ArgumentCaptor<byte[]> config = ArgumentCaptor.forClass(byte[].class);
        verify(panel).putFile(eq(PANEL_ID), eq("config/wtc_lodestone-common.toml"), config.capture());
        assertThat(new String(config.getValue(), StandardCharsets.UTF_8).lines())
                .contains("hubUrl = \"http://127.0.0.1:8081\"", "serverId = \"prov-test\"", "token = \"" + created.token() + "\"",
                        "syncWhitelist = true", "syncBans = true");
        verify(panel).putFile(PANEL_ID, "mods/wtc_lodestone-neoforge-1.21.1-1.0.0.jar", new byte[] {1, 2, 3});
        verify(panel).install(PANEL_ID);
    }

    @Test
    void anUnknownOrInternalSettingIsRefusedBeforeAnythingIsCreated() {
        assertThatThrownBy(() -> provisioning.create(new ServerProvisioningService.CreateRequest(
                "neoforge", null, "prov-test", "Provisioned", Map.of("secret", "y")), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no setting 'secret'");
        verify(panel, never()).createServer(any());
    }

    @Test
    void aTakenHubIdIsRefusedBeforeThePanelIsAsked() {
        provisioning.create(new ServerProvisioningService.CreateRequest("neoforge", null, "prov-test", "Provisioned", Map.of()), "admin");

        assertThatThrownBy(() -> provisioning.create(new ServerProvisioningService.CreateRequest(
                "neoforge", null, "prov-test", "Again", Map.of()), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("already exists");
        verify(panel).createServer(any()); // only the first one
    }

    @Test
    void aMissingJarIsAWarningNotAFailure() {
        when(modRelease.latestNeoForgeJar()).thenThrow(new IllegalStateException("couldn't reach GitHub"));

        var created = provisioning.create(new ServerProvisioningService.CreateRequest(
                "neoforge", null, "prov-test", "Provisioned", Map.of()), "admin");

        assertThat(created.warnings()).singleElement().asString().contains("mod jar", "couldn't reach GitHub");
        verify(panel).install(PANEL_ID);
    }

    @Test
    void aFailedRegistrationTakesThePanelServerBackDown() {
        // the panel "loses" the server before the hub can link it, so registering fails
        when(panel.listServers()).thenReturn(List.of());

        assertThatThrownBy(() -> provisioning.create(new ServerProvisioningService.CreateRequest(
                "neoforge", null, "prov-test", "Provisioned", Map.of()), "admin"))
                .isInstanceOf(ResponseStatusException.class);
        verify(panel).deleteServer(PANEL_ID);
        verify(panel, never()).putFile(anyString(), anyString(), any());
    }

    @Test
    void withoutTheServerHubUrlNothingIsCreated() {
        when(panel.serverHubUrl()).thenReturn(null);

        assertThatThrownBy(() -> provisioning.create(new ServerProvisioningService.CreateRequest(
                "neoforge", null, "prov-test", "Provisioned", Map.of()), "admin"))
                .hasMessageContaining("LODESTONE_PUFFERPANEL_SERVER_HUB_URL");
        verify(panel, never()).createServer(any());
    }
}
