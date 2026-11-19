package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ModReleaseTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer github = MockRestServiceServer.bindTo(builder).build();

    @Test
    void asksForTheRepoWithItsSlashIntactAndDownloadsTheMatchingJar() {
        // the slash in "owner/name" must stay a slash: as %2F, GitHub answers 404
        github.expect(requestTo("https://api.github.com/repos/Falphir/WTC_Lodestone/releases/latest"))
                .andRespond(withSuccess("""
                        {"tag_name":"v0.1.0","assets":[
                          {"name":"wtc_lodestone-hub.jar","browser_download_url":"https://example.test/hub.jar"},
                          {"name":"wtc_lodestone-neoforge-1.21.1-1.0.0.jar","browser_download_url":"https://example.test/neoforge.jar"},
                          {"name":"wtc_lodestone-forge-1.20.1-1.0.0.jar","browser_download_url":"https://example.test/forge.jar"}]}
                        """, MediaType.APPLICATION_JSON));
        github.expect(requestTo("https://example.test/forge.jar"))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));

        ModRelease.Jar jar = new ModRelease("Falphir/WTC_Lodestone", builder).jarFor("forge", "1.20.1").orElseThrow();

        // "forge" isn't mistaken for the end of "neoforge"
        assertThat(jar.fileName()).isEqualTo("wtc_lodestone-forge-1.20.1-1.0.0.jar");
        assertThat(jar.bytes()).containsExactly(1, 2, 3);
        github.verify();
    }

    @Test
    void aReleaseWithoutTheBuildHasNoJarForIt() {
        github.expect(requestTo("https://api.github.com/repos/Falphir/WTC_Lodestone/releases/latest"))
                .andRespond(withSuccess("""
                        {"assets":[{"name":"wtc_lodestone-neoforge-1.21.1-1.0.0.jar","browser_download_url":"https://example.test/neoforge.jar"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(new ModRelease("Falphir/WTC_Lodestone", builder).jarFor("neoforge", "1.21")).isEmpty();
    }

    @Test
    void anAnswerFromGitHubIsReportedAsWhatItWasNotAsUnreachable() {
        github.expect(requestTo("https://api.github.com/repos/Falphir/WTC_Lodestone/releases/latest"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> new ModRelease("Falphir/WTC_Lodestone", builder).jarFor("neoforge", "1.21.1"))
                .hasMessageContaining("GitHub answered HTTP 404");
    }
}
