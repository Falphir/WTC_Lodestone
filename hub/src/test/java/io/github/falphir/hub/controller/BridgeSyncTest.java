package io.github.falphir.hub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.falphir.hub.repository.WhitelistedPlayerRepository;
import io.github.falphir.hub.service.AdminUserService;
import io.github.falphir.hub.service.ServerService;
import io.github.falphir.hub.service.WhitelistService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BridgeSyncTest {

    private static final String SERVER = "sync-test";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ServerService servers;

    @Autowired
    private WhitelistService whitelist;

    @Autowired
    private AdminUserService admins;

    @Autowired
    private WhitelistedPlayerRepository whitelistRows;

    @Test
    void serverReportsSetupAndWhitelistVersionUntilTheListChanges() throws Exception {
        String bearer = "Bearer " + servers.register(SERVER, "Sync Test", null).token();
        String admin = adminToken();

        mvc.perform(post("/api/bridge/hello").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modVersion\":\"1.2.0\",\"minecraftVersion\":\"1.21.1\",\"loaderVersion\":\"21.1.231\",\"syncWhitelist\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heartbeatIntervalSeconds").value(60))
                .andExpect(jsonPath("$.whitelistSyncIntervalSeconds").value(10));

        whitelist.importEntries(List.of(new WhitelistService.Entry("0f3a1b2c-0000-4000-8000-0000000000b1", "Alice")), "test");
        String version = mvc.perform(get("/api/bridge/whitelist").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(header().exists(BridgeController.WHITELIST_VERSION_HEADER))
                .andReturn().getResponse().getHeader(BridgeController.WHITELIST_VERSION_HEADER);

        mvc.perform(post("/api/bridge/whitelist/applied").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"" + version + "\"}"))
                .andExpect(status().isOk());

        assertThat(serverView(admin, "$.mod.version")).isEqualTo("1.2.0");
        assertThat(serverView(admin, "$.whitelist.enabled")).isEqualTo(true);
        assertThat(serverView(admin, "$.whitelist.current")).isEqualTo(true);
        assertThat(serverView(admin, "$.status")).isEqualTo("ONLINE");

        whitelist.importEntries(List.of(new WhitelistService.Entry("0f3a1b2c-0000-4000-8000-0000000000b2", "Bob")), "test");
        assertThat(serverView(admin, "$.whitelist.current")).isEqualTo(false);
    }

    @Test
    void whitelistStaysVisibleAsEmptyOnceItHasEverBeenPopulated() throws Exception {
        String bearer = "Bearer " + servers.register(SERVER + "-empty", "Sync Test Empty", null).token();
        String uuid = "0f3a1b2c-0000-4000-8000-0000000000c1";

        whitelistRows.deleteAllInBatch();

        whitelist.importEntries(List.of(new WhitelistService.Entry(uuid, "Carl")), "test");
        mvc.perform(get("/api/bridge/whitelist").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        whitelist.remove(uuid);

        mvc.perform(get("/api/bridge/whitelist").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(header().exists(BridgeController.WHITELIST_VERSION_HEADER))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void kickMessageSetOnTheDashboardReachesServersUntilCleared() throws Exception {
        String bearer = "Bearer " + servers.register(SERVER, "Sync Test", null).token();
        String admin = adminToken();
        String heartbeat = "{\"playerCount\":0,\"maxPlayers\":20,\"tps\":20,\"msptAvg\":5,\"memoryUsedMb\":1,\"memoryMaxMb\":2}";

        mvc.perform(put("/api/admin/whitelist/kick-message").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  Not whitelisted.\\nApply on Discord.  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Not whitelisted.\nApply on Discord."));

        mvc.perform(post("/api/bridge/hello").header("Authorization", bearer))
                .andExpect(jsonPath("$.whitelistMessage").value("Not whitelisted.\nApply on Discord."));
        mvc.perform(post("/api/bridge/heartbeat").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(heartbeat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whitelistMessage").value("Not whitelisted.\nApply on Discord."));

        // blank hands each server back to its own config
        mvc.perform(put("/api/admin/whitelist/kick-message").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(jsonPath("$.message").value(""));
        mvc.perform(post("/api/bridge/heartbeat").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(heartbeat))
                .andExpect(jsonPath("$.whitelistMessage").value(""));
    }

    @Test
    void aCodeFromTheKickScreenLinksAndWhitelistsTheAccountOnce() throws Exception {
        String bearer = "Bearer " + servers.register(SERVER, "Sync Test", null).token();
        String admin = adminToken();
        String first = "0f3a1b2c-0000-4000-8000-0000000000d1";
        String second = "0f3a1b2c-0000-4000-8000-0000000000d2";

        offerCode(bearer, "100002", first, "LinkTester");
        claimCode(admin, "100002", "900000000000000001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(first))
                .andExpect(jsonPath("$.linkedDiscordId").value("900000000000000001"));
        claimCode(admin, "100002", "900000000000000001").andExpect(status().isNotFound()); // single use

        // someone else can't take an account that's already linked
        offerCode(bearer, "100003", first, "LinkTester");
        claimCode(admin, "100003", "900000000000000002").andExpect(status().isConflict());

        // switching to another account drops the old one from the whitelist
        offerCode(bearer, "100004", second, "LinkTester2");
        claimCode(admin, "100004", "900000000000000001").andExpect(jsonPath("$.uuid").value(second));
        assertThat(whitelistRows.existsById(first)).isFalse();
    }

    @Test
    void tooManyWrongCodesFromOneDiscordAccountAreRefused() throws Exception {
        String admin = adminToken();
        String guesser = "900000000000000099";
        for (int i = 0; i < 5; i++) {
            claimCode(admin, String.format("%06d", 999000 + i), guesser).andExpect(status().isNotFound());
        }
        claimCode(admin, "999999", guesser).andExpect(status().isTooManyRequests());
    }

    private void offerCode(String bearer, String code, String uuid, String name) throws Exception {
        mvc.perform(post("/api/bridge/link-code").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"uuid\":\"" + uuid + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions claimCode(String admin, String code, String discordId) throws Exception {
        return mvc.perform(post("/api/admin/whitelist/link-code").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"discordId\":\"" + discordId + "\"}"));
    }

    @Test
    void olderModsCanStillCheckInWithoutABody() throws Exception {
        String bearer = "Bearer " + servers.register(SERVER, "Sync Test", null).token();
        mvc.perform(post("/api/bridge/hello").header("Authorization", bearer)).andExpect(status().isOk());
    }

    @Test
    void queryTokenOnlyWorksForTheLiveSocket() throws Exception {
        String token = adminToken().substring("Bearer ".length());
        mvc.perform(get("/api/admin/servers").param("access_token", token)).andExpect(status().isUnauthorized());
        // past security; MockMvc can't upgrade to a WebSocket, so the handler itself answers with a client error
        int live = mvc.perform(get("/api/admin/live").param("access_token", token)).andReturn().getResponse().getStatus();
        assertThat(live).isNotIn(401, 403);
        mvc.perform(get("/api/admin/live")).andExpect(status().isUnauthorized());
    }

    private String adminToken() throws Exception {
        admins.create("sync-tester", "sync-tester-password");
        String body = mvc.perform(post("/api/auth/token").with(httpBasic("sync-tester", "sync-tester-password")))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private Object serverView(String admin, String path) throws Exception {
        String body = mvc.perform(get("/api/admin/servers").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        // a filter always yields a list: e.g. $[?(@.id == 'sync-test')].mod.version -> ["1.2.0"]
        List<Object> matches = JsonPath.read(body, "$[?(@.id == '" + SERVER + "')]" + path.substring(1));
        return matches.get(0);
    }
}
