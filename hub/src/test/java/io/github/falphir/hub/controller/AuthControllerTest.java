package io.github.falphir.hub.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.falphir.hub.service.AdminUserService;
import io.github.falphir.hub.service.ServerService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AdminUserService admins;

    @Autowired
    private ServerService servers;

    @Test
    void passwordGetsTokenThatOpensAdminApiButCannotRenewItself() throws Exception {
        admins.create("jwt-tester", "jwt-tester-password");

        String body = mvc.perform(post("/api/auth/token").with(basic("jwt-tester", "jwt-tester-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(body, "$.token");

        mvc.perform(get("/api/admin/servers").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/token").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/servers").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/token").with(basic("jwt-tester", "wrong"))).andExpect(status().isUnauthorized());
    }

    @Test
    void serverTokensStillWorkOnBridgeAndNotOnAdminApi() throws Exception {
        String bearer = "Bearer " + servers.register("jwt-bridge-test", "JWT Bridge Test", null).token();

        mvc.perform(get("/api/bridge/whitelist").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/servers").header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor basic(String user, String password) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic(user, password);
    }
}
