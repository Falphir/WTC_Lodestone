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

    @MockitoBean
    private CurseForgeClient curseForge;

    @MockitoBean
    private ModpackSetup modpackSetup;

    @Autowired
    private MotdService motds;

    private static final CurseForgeClient.Modpack PACK = new CurseForgeClient.Modpack(
            100, "All the Mods 10", "kitchen sink", "https://media.forgecdn.net/atm10.png", "https://www.curseforge.com/minecraft/modpacks/atm10", true);
    private static final CurseForgeClient.PackFile PACK_FILE = new CurseForgeClient.PackFile(200, 201, "ATM10-5.1", List.of("1.21.1"), "2026-10-01T00:00:00Z");

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
                "neoforge", null, "prov-test", "Provisioned", Map.of("memory", 4096, "port", 25570), null), "admin");

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
        assertThat(created.modpackPending()).isFalse();
        verify(modpackSetup, never()).start(any(), any(), any(), any());
    }

    @Test
    void aModpackFillsInTheListingAndLeavesTheInstallToItsSetup() {
        when(curseForge.isConfigured()).thenReturn(true);
        when(curseForge.modpack(100)).thenReturn(PACK);
        when(curseForge.file(100, 200)).thenReturn(PACK_FILE);

        var created = provisioning.create(new ServerProvisioningService.CreateRequest("neoforge", null, "prov-test", "Provisioned",
                Map.of(), new ServerProvisioningService.Modpack(100, 200)), "admin");

        assertThat(created.modpackPending()).isTrue();
        var server = servers.findById("prov-test").orElseThrow();
        assertThat(server.getModpack()).isEqualTo("All the Mods 10");
        assertThat(server.getModpackVersion()).isEqualTo("5.1"); // just the version out of "ATM10-5.1"
        assertThat(server.getModpackUrl()).isEqualTo(PACK.websiteUrl());
        assertThat(server.getIconUrl()).isEqualTo(PACK.logoUrl());
        assertThat(server.getLauncher()).isEqualTo("CurseForge");
        verify(modpackSetup).start("prov-test", PANEL_ID, PACK, PACK_FILE);
        verify(panel, never()).install(anyString()); // runs once the pack's files are in
    }

    @Test
    void aPacksSetupIsTheMatchingTemplateWithItsSettingsFilledIn() {
        when(curseForge.isConfigured()).thenReturn(true);
        when(curseForge.manifest(100, 200)).thenReturn(new CurseForgeClient.PackManifest("1.21.1", "neoforge", "21.1.265", 8196));
        when(panel.templates()).thenReturn(List.of(Map.of("name", "minecraft-neoforge"), Map.of("name", "minecraft-forge")));
        when(panel.template("minecraft-neoforge")).thenReturn(Map.of("data", Map.of(
                "version", Map.of("value", "21.1.209"), "javaversion", Map.of("value", "21"), "memory", Map.of("value", 1024))));

        var setup = provisioning.packSetup(100, 200);

        assertThat(setup.template()).isEqualTo("minecraft-neoforge");
        assertThat(setup.values()).containsEntry("version", "21.1.265").containsEntry("memory", 8196).containsEntry("javaversion", "21");
        verify(panel, never()).template("minecraft-forge"); // only the matched template is looked at
    }

    @Test
    @SuppressWarnings("unchecked")
    void theChosenMotdIsWrittenIntoTheTemplateFilledInFromThePack() {
        when(curseForge.isConfigured()).thenReturn(true);
        when(curseForge.modpack(100)).thenReturn(PACK);
        when(curseForge.file(100, 200)).thenReturn(new CurseForgeClient.PackFile(200, 201, "All the Mods 10-8.4", List.of("1.21.1"), null));
        when(panel.template("neoforge")).thenReturn(Map.of("name", "neoforge", "data", Map.of(
                "motd", Map.of("type", "string", "value", "A Minecraft Server"),
                "memory", Map.of("type", "integer", "value", 2048))));
        long motd = motds.create("provisioning-test-motd", "§6WTC\\n§2{modpack} §8| §7v{version}").id();

        provisioning.create(new ServerProvisioningService.CreateRequest("neoforge", null, "prov-test", "Provisioned", Map.of(),
                new ServerProvisioningService.Modpack(100, 200), motd), "admin");

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(panel).createServer(body.capture());
        var data = (Map<String, Object>) body.getValue().get("data");
        // the line break as server.properties writes it, and just the version out of the version's name
        assertThat(((Map<String, Object>) data.get("motd")).get("value")).isEqualTo("§6WTC\\n§2All the Mods 10 §8| §7v8.4");
        var server = servers.findById("prov-test").orElseThrow();
        assertThat(server.getMotdTemplateId()).isEqualTo(motd);
        assertThat(server.getModpackVersion()).isEqualTo("8.4");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aMotdTypedIntoTheFormWinsOverTheChosenOne() {
        when(panel.template("neoforge")).thenReturn(Map.of("name", "neoforge", "data", Map.of(
                "motd", Map.of("type", "string", "value", "A Minecraft Server"))));
        long motd = motds.create("provisioning-test-motd-2", "Welcome to {name}").id();

        provisioning.create(new ServerProvisioningService.CreateRequest("neoforge", null, "prov-test", "Provisioned",
                Map.of("motd", "Mine"), null, motd), "admin");

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(panel).createServer(body.capture());
        assertThat(((Map<String, Object>) ((Map<String, Object>) body.getValue().get("data")).get("motd")).get("value")).isEqualTo("Mine");
    }

    @Test
    void aPackThatCantBeDownloadedIsRefusedBeforeAnythingIsCreated() {
        when(curseForge.isConfigured()).thenReturn(true);
        when(curseForge.modpack(100)).thenReturn(new CurseForgeClient.Modpack(100, "Locked", null, null, null, false));
        when(curseForge.file(100, 200)).thenReturn(PACK_FILE);

        assertThatThrownBy(() -> provisioning.create(new ServerProvisioningService.CreateRequest("neoforge", null, "prov-test",
                "Provisioned", Map.of(), new ServerProvisioningService.Modpack(100, 200)), "admin"))
                .hasMessageContaining("CurseForge app");
        verify(panel, never()).createServer(any());
    }

    @Test
    void aModpackWithoutCurseForgeConfiguredIsRefused() {
        when(curseForge.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> provisioning.create(new ServerProvisioningService.CreateRequest("neoforge", null, "prov-test",
                "Provisioned", Map.of(), new ServerProvisioningService.Modpack(100, 200)), "admin"))
                .hasMessageContaining("CurseForge isn't configured");
        verify(panel, never()).createServer(any());
    }

    @Test
    void anUnknownOrInternalSettingIsRefusedBeforeAnythingIsCreated() {
        assertThatThrownBy(() -> provisioning.create(new ServerProvisioningService.CreateRequest(
                "neoforge", null, "prov-test", "Provisioned", Map.of("secret", "y"), null), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no setting 'secret'");
        verify(panel, never()).createServer(any());
    }

    @Test
    void aTakenHubIdIsRefusedBeforeThePanelIsAsked() {
        provisioning.create(new ServerProvisioningService.CreateRequest("neoforge", null, "prov-test", "Provisioned", Map.of(), null), "admin");

        assertThatThrownBy(() -> provisioning.create(new ServerProvisioningService.CreateRequest(
                "neoforge", null, "prov-test", "Again", Map.of(), null), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("already exists");
        verify(panel).createServer(any()); // only the first one
    }

    @Test
    void aMissingJarIsAWarningNotAFailure() {
        when(modRelease.latestNeoForgeJar()).thenThrow(new IllegalStateException("couldn't reach GitHub"));

        var created = provisioning.create(new ServerProvisioningService.CreateRequest(
                "neoforge", null, "prov-test", "Provisioned", Map.of(), null), "admin");

        assertThat(created.warnings()).singleElement().asString().contains("mod jar", "couldn't reach GitHub");
        verify(panel).install(PANEL_ID);
    }

    @Test
    void aFailedRegistrationTakesThePanelServerBackDown() {
        // the panel "loses" the server before the hub can link it, so registering fails
        when(panel.listServers()).thenReturn(List.of());

        assertThatThrownBy(() -> provisioning.create(new ServerProvisioningService.CreateRequest(
                "neoforge", null, "prov-test", "Provisioned", Map.of(), null), "admin"))
                .isInstanceOf(ResponseStatusException.class);
        verify(panel).deleteServer(PANEL_ID);
        verify(panel, never()).putFile(anyString(), anyString(), any(byte[].class));
    }

    @Test
    void withoutTheServerHubUrlNothingIsCreated() {
        when(panel.serverHubUrl()).thenReturn(null);

        assertThatThrownBy(() -> provisioning.create(new ServerProvisioningService.CreateRequest(
                "neoforge", null, "prov-test", "Provisioned", Map.of(), null), "admin"))
                .hasMessageContaining("LODESTONE_PUFFERPANEL_SERVER_HUB_URL");
        verify(panel, never()).createServer(any());
    }
}
