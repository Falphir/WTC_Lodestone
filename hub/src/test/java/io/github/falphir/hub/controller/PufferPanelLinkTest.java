package io.github.falphir.hub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.falphir.hub.service.AdminUserService;
import io.github.falphir.hub.service.PufferPanelClient;
import io.github.falphir.hub.service.PufferPanelClient.PanelServer;

/** The hub side of the PufferPanel link, with the panel itself mocked out. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PufferPanelLinkTest {

    private static final String GENESIS = "ppla1111";
    private static final String SKYBLOCK = "pplb2222";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AdminUserService admins;

    @MockitoBean
    private PufferPanelClient panel;

    private String admin;

    @BeforeEach
    void panelWithTwoServers() throws Exception {
        when(panel.isConfigured()).thenReturn(true);
        when(panel.listServers()).thenReturn(List.of(
                new PanelServer(GENESIS, "Genesis", 1, null, 25565, "minecraft-java"),
                new PanelServer(SKYBLOCK, "Skyblock", 1, null, 25566, "minecraft-java")));
        admins.create("panel-link-tester", "panel-link-tester-password");
        String body = mvc.perform(post("/api/auth/token").with(httpBasic("panel-link-tester", "panel-link-tester-password")))
                .andReturn().getResponse().getContentAsString();
        admin = "Bearer " + JsonPath.read(body, "$.token");
    }

    @Test
    void registeringWithAPanelServerLinksItAndThePanelListSaysWho() throws Exception {
        register("pp-link-a", GENESIS).andExpect(status().isCreated());

        assertThat(serverField("pp-link-a", "pufferpanelId")).isEqualTo(GENESIS);
        assertThat(linkedTo(GENESIS)).isEqualTo("pp-link-a");
        assertThat(linkedTo(SKYBLOCK)).isNull();
    }

    @Test
    void aPanelServerBelongsToOneHubServerAndMustExist() throws Exception {
        register("pp-link-a", GENESIS).andExpect(status().isCreated());

        register("pp-link-b", GENESIS).andExpect(status().isConflict());
        register("pp-link-c", "nothere1").andExpect(status().isNotFound());
    }

    @Test
    void editingWithAnEmptyIdUnlinksAndARenameKeepsTheLink() throws Exception {
        register("pp-link-a", GENESIS).andExpect(status().isCreated());

        edit("pp-link-a", "{\"id\":\"pp-link-renamed\"}").andExpect(status().isOk());
        assertThat(linkedTo(GENESIS)).isEqualTo("pp-link-renamed");

        edit("pp-link-renamed", "{\"pufferpanelId\":\"\"}").andExpect(status().isOk());
        assertThat(linkedTo(GENESIS)).isNull();
        assertThat(serverField("pp-link-renamed", "pufferpanelId")).isNull();
    }

    @Test
    void withoutPufferPanelTheListIsEmptyAndLinkingIsRefused() throws Exception {
        when(panel.isConfigured()).thenReturn(false);

        mvc.perform(get("/api/admin/pufferpanel/servers").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        register("pp-link-a", GENESIS).andExpect(status().isServiceUnavailable());
        register("pp-link-plain", null).andExpect(status().isCreated()); // unlinked servers are unaffected
    }

    private ResultActions register(String id, String pufferpanelId) throws Exception {
        String link = pufferpanelId == null ? "" : ",\"pufferpanelId\":\"" + pufferpanelId + "\"";
        return mvc.perform(post("/api/admin/servers").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"name\":\"Link Test\"" + link + "}"));
    }

    private ResultActions edit(String id, String json) throws Exception {
        return mvc.perform(patch("/api/admin/servers/" + id).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Object serverField(String id, String field) throws Exception {
        String body = mvc.perform(get("/api/admin/servers").header("Authorization", admin)).andReturn().getResponse().getContentAsString();
        List<Object> matches = JsonPath.read(body, "$[?(@.id == '" + id + "')]." + field);
        return matches.get(0);
    }

    private Object linkedTo(String panelId) throws Exception {
        String body = mvc.perform(get("/api/admin/pufferpanel/servers").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Object> matches = JsonPath.read(body, "$[?(@.id == '" + panelId + "')].linkedTo");
        return matches.get(0);
    }
}
