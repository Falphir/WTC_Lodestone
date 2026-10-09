package io.github.falphir.hub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

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

    @Test
    void serverReportsSetupAndWhitelistVersionUntilTheListChanges() throws Exception {
        String bearer = "Bearer " + servers.register(SERVER, "Sync Test", null).token();
        String admin = adminToken();

        mvc.perform(post("/api/bridge/hello").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modVersion\":\"1.2.0\",\"minecraftVersion\":\"1.21.1\",\"loaderVersion\":\"21.1.231\",\"syncWhitelist\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heartbeatIntervalSeconds").value(60));

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

        whitelist.importEntries(List.of(new WhitelistService.Entry(uuid, "Carl")), "test");
        mvc.perform(get("/api/bridge/whitelist").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        whitelist.remove(uuid);

        // The list is genuinely empty now, but it HAS been populated before -- unlike a fresh hub
        // that's never had anyone added, this must come back as 200 + [], not 204, or servers would
        // never learn the last player was removed (see HubClient.applyWhitelist on the mod side).
        mvc.perform(get("/api/bridge/whitelist").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(header().exists(BridgeController.WHITELIST_VERSION_HEADER))
                .andExpect(jsonPath("$.length()").value(0));
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
