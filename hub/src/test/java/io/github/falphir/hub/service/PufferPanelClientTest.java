package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import io.github.falphir.hub.config.LodestoneProperties;

/** Against a mock panel speaking PufferPanel 2.7.1's token and server-list shapes. */
class PufferPanelClientTest {

    private static final String PANEL = "http://panel.test";
    private static final LodestoneProperties.PufferPanel CONFIG =
            new LodestoneProperties.PufferPanel(PANEL + "/", "client-id", "s3cret");

    private static final String TOKEN = "{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
    private static final String TWO_SERVERS = "{\"servers\":[{\"id\":\"aaaa1111\",\"name\":\"Genesis\",\"nodeId\":1,\"type\":\"minecraft-java\"},"
            + "{\"id\":\"bbbb2222\",\"name\":\"Skyblock\",\"nodeId\":1,\"type\":\"minecraft-java\"}],"
            + "\"paging\":{\"page\":1,\"pageSize\":100,\"maxSize\":100,\"total\":2}}";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer panel = MockRestServiceServer.bindTo(builder).build();

    @Test
    void signsInOnceWithAFormAndReusesTheToken() {
        panel.expect(ExpectedCount.once(), requestTo(PANEL + "/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "client_credentials", "client_id", "client-id", "client_secret", "s3cret")))
                .andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        panel.expect(ExpectedCount.twice(), requestTo(PANEL + "/api/servers?limit=100&page=1"))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withSuccess(TWO_SERVERS, MediaType.APPLICATION_JSON));

        PufferPanelClient client = new PufferPanelClient(CONFIG, builder);
        assertThat(client.listServers()).extracting(PufferPanelClient.PanelServer::name).containsExactly("Genesis", "Skyblock");
        assertThat(client.listServers()).hasSize(2);
        panel.verify();
    }

    @Test
    void aStaleTokenGetsOneRetryWithAFreshOne() {
        panel.expect(requestTo(PANEL + "/oauth2/token")).andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        panel.expect(requestTo(PANEL + "/api/servers?limit=100&page=1")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        panel.expect(requestTo(PANEL + "/oauth2/token"))
                .andRespond(withSuccess(TOKEN.replace("tok-1", "tok-2"), MediaType.APPLICATION_JSON));
        panel.expect(requestTo(PANEL + "/api/servers?limit=100&page=1"))
                .andExpect(header("Authorization", "Bearer tok-2"))
                .andRespond(withSuccess(TWO_SERVERS, MediaType.APPLICATION_JSON));

        assertThat(new PufferPanelClient(CONFIG, builder).listServers()).hasSize(2);
        panel.verify();
    }

    @Test
    void aWrongSecretIsWordedForTheAdmin() {
        panel.expect(requestTo(PANEL + "/oauth2/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_client\"}"));

        assertThatThrownBy(() -> new PufferPanelClient(CONFIG, builder).listServers())
                .isInstanceOf(PufferPanelClient.PanelException.class)
                .hasMessage("PufferPanel rejected the client id/secret");
    }

    @Test
    void followsPagingUntilItHasEveryServer() {
        panel.expect(requestTo(PANEL + "/oauth2/token")).andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        panel.expect(requestTo(PANEL + "/api/servers?limit=100&page=1")).andRespond(withSuccess(
                "{\"servers\":[{\"id\":\"aaaa1111\",\"name\":\"Genesis\"}],\"paging\":{\"page\":1,\"pageSize\":1,\"total\":2}}",
                MediaType.APPLICATION_JSON));
        panel.expect(requestTo(PANEL + "/api/servers?limit=100&page=2")).andRespond(withSuccess(
                "{\"servers\":[{\"id\":\"bbbb2222\",\"name\":\"Skyblock\"}],\"paging\":{\"page\":2,\"pageSize\":1,\"total\":2}}",
                MediaType.APPLICATION_JSON));

        assertThat(new PufferPanelClient(CONFIG, builder).listServers()).extracting(PufferPanelClient.PanelServer::id)
                .containsExactly("aaaa1111", "bbbb2222");
    }

    @Test
    void unsetMeansNothingCallsOut() {
        PufferPanelClient client = new PufferPanelClient(new LodestoneProperties.PufferPanel("", "", ""), builder);

        assertThat(client.isConfigured()).isFalse();
        assertThat(client.url()).isNull();
        assertThatThrownBy(client::listServers).hasMessage("PufferPanel isn't configured");
        panel.verify(); // no requests were expected, and none were made
    }
}
