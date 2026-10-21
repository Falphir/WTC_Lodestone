package io.github.falphir.hub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.falphir.hub.repository.NetworkBanRepository;
import io.github.falphir.hub.service.AdminUserService;
import io.github.falphir.hub.service.BanService;
import io.github.falphir.hub.service.MojangProfiles;
import io.github.falphir.hub.service.ServerService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BanSyncTest {

    private static final String SERVER = "ban-test";
    private static final String GRIEFER = "0f3a1b2c-0000-4000-8000-0000000000e1";
    private static final String TEMP = "0f3a1b2c-0000-4000-8000-0000000000e2";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ServerService servers;

    @Autowired
    private AdminUserService admins;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NetworkBanRepository bans;

    @MockitoBean
    private MojangProfiles mojang;

    @Test
    void aBanReachesServersAndTheDashboardSeesWhoAppliedIt() throws Exception {
        String bearer = "Bearer " + servers.register(SERVER, "Ban Test", null).token();
        String admin = adminToken();
        when(mojang.lookup("Griefer")).thenReturn(Optional.of(new MojangProfiles.Profile(GRIEFER, "Griefer")));

        mvc.perform(post("/api/admin/bans").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Griefer\",\"reason\":\"x-ray\"}"))
                .andExpect(status().isCreated());

        var listed = mvc.perform(get("/api/bridge/bans").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        List<String> uuids = JsonPath.read(listed.getContentAsString(), "$[*].uuid");
        assertThat(uuids).contains(GRIEFER);
        String version = listed.getHeader(BridgeController.BAN_VERSION_HEADER);
        assertThat(version).isNotBlank();

        mvc.perform(post("/api/bridge/bans/applied").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"" + version + "\"}"))
                .andExpect(status().isOk());
        assertThat(banSync(admin, "$.bans.current")).isEqualTo(true);

        mvc.perform(delete("/api/admin/bans/" + GRIEFER).header("Authorization", admin)).andExpect(status().isNoContent());
        String after = mvc.perform(get("/api/bridge/bans").header("Authorization", bearer)).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(after, "$[*].uuid")).doesNotContain(GRIEFER);
        assertThat(banSync(admin, "$.bans.current")).isEqualTo(false); // what it applied still has the ban
    }

    @Test
    void anExpiredBanIsNotSentToServers() throws Exception {
        String bearer = "Bearer " + servers.register(SERVER, "Ban Test", null).token();
        String admin = adminToken();
        when(mojang.lookup("Temp")).thenReturn(Optional.of(new MojangProfiles.Profile(TEMP, "Temp")));

        String tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        mvc.perform(post("/api/admin/bans").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Temp\",\"expiresAt\":\"" + tomorrow + "\"}"))
                .andExpect(status().isCreated());
        // a ban can't be created already expired, so let this one run out (flushed first, or the
        // pending insert would land after this update and undo it)
        bans.flush();
        jdbc.update("UPDATE network_bans SET expires_at = ? WHERE uuid = ?",
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), TEMP);

        String listed = mvc.perform(get("/api/bridge/bans").header("Authorization", bearer)).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(listed, "$[*].uuid")).doesNotContain(TEMP);
    }

    @Test
    void theBanMessageReachesServersAndBlankGoesBackToTheDefault() throws Exception {
        String bearer = "Bearer " + servers.register(SERVER, "Ban Test", null).token();
        String admin = adminToken();

        mvc.perform(put("/api/admin/bans/message").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"&4Banned: {reason}\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/bridge/hello").header("Authorization", bearer))
                .andExpect(jsonPath("$.banMessage").value("&4Banned: {reason}"));

        mvc.perform(put("/api/admin/bans/message").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(jsonPath("$.message").value(BanService.DEFAULT_MESSAGE));
    }

    private String adminToken() throws Exception {
        admins.create("ban-tester", "ban-tester-password");
        String body = mvc.perform(post("/api/auth/token").with(httpBasic("ban-tester", "ban-tester-password")))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private Object banSync(String admin, String path) throws Exception {
        String body = mvc.perform(get("/api/admin/servers").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        List<Object> matches = JsonPath.read(body, "$[?(@.id == '" + SERVER + "')]" + path.substring(1));
        return matches.get(0);
    }
}
