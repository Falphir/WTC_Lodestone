package io.github.falphir.hub.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.falphir.hub.service.AdminUserService;

/** The decision endpoint as the dashboard calls it: JSON in, answers back out as an object. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApplicationDecisionTest {

    private static final String APPS = "/api/admin/discord/applications/";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AdminUserService admins;

    @Test
    void aDashboardDecisionWithoutAppliedWaitsForTheBot() throws Exception {
        String bearer = "Bearer " + adminToken();
        mvc.perform(put(APPS + "test-http-app/pending").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":\"m\",\"discordName\":\"alice\",\"answers\":{\"mc\":\"Alice\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.mc").value("Alice"));

        // the dashboard sends no `applied`, which a primitive boolean would reject
        mvc.perform(put(APPS + "test-http-app/decision").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"denied\",\"reason\":\"full\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botPending").value(true))
                .andExpect(jsonPath("$.denyReason").value("full"));

        mvc.perform(put(APPS + "test-http-app/decision").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"approved\"}"))
                .andExpect(status().isConflict());
    }

    private String adminToken() throws Exception {
        admins.create("app-tester", "app-tester-password");
        String body = mvc.perform(post("/api/auth/token").with(httpBasic("app-tester", "app-tester-password")))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
