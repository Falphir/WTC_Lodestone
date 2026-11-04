package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import io.github.falphir.hub.config.LodestoneProperties;

class CurseForgeClientTest {

    private static final String KEY = "cf-s3cret";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer curseForge = MockRestServiceServer.bindTo(builder).build();
    private final CurseForgeClient client = new CurseForgeClient(new LodestoneProperties.CurseForge(KEY), builder);

    @TempDir
    Path dir;

    @Test
    void searchesMinecraftModpacksWithTheKeyAndTellsWhichCanBeDownloaded() {
        curseForge.expect(requestTo(CurseForgeClient.API
                        + "/v1/mods/search?gameId=432&classId=4471&searchFilter=all%20the%20mods&sortField=2&sortOrder=desc&pageSize=20"))
                .andExpect(header("x-api-key", KEY))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":1,"name":"All the Mods 10","summary":"s","allowModDistribution":true,
                           "logo":{"thumbnailUrl":"https://media.forgecdn.net/t.png"},"links":{"websiteUrl":"https://cf/atm10"}},
                          {"id":2,"name":"Locked","allowModDistribution":false}]}
                        """, MediaType.APPLICATION_JSON));

        var packs = client.searchModpacks(" all the mods ");

        assertThat(packs).extracting(CurseForgeClient.Modpack::name).containsExactly("All the Mods 10", "Locked");
        assertThat(packs.get(0).logoUrl()).isEqualTo("https://media.forgecdn.net/t.png");
        assertThat(packs.get(0).downloadable()).isTrue();
        assertThat(packs.get(1).downloadable()).isFalse();
    }

    @Test
    void listsOnlyVersionsWithServerFilesNewestFirst() {
        curseForge.expect(requestTo(CurseForgeClient.API + "/v1/mods/1/files?pageSize=50"))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":10,"displayName":"5.0","fileDate":"2026-09-01T00:00:00Z","gameVersions":["1.21.1","NeoForge"],"serverPackFileId":11},
                          {"id":20,"displayName":"5.1-client-only","fileDate":"2026-10-15T00:00:00Z","serverPackFileId":null},
                          {"id":30,"displayName":"5.1.zip","fileDate":"2026-10-01T00:00:00Z","gameVersions":["1.21.1"],"serverPackFileId":31}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.serverFiles(1)).extracting(CurseForgeClient.PackFile::displayName).containsExactly("5.1", "5.0");
    }

    @Test
    void downloadsAFileByTheLinkCurseForgeGivesForIt() throws IOException {
        curseForge.expect(requestTo(CurseForgeClient.API + "/v1/mods/1/files/11/download-url"))
                .andRespond(withSuccess("{\"data\":\"https://edge.forgecdn.net/files/1/11/ATM10 Server 5.0.zip\"}", MediaType.APPLICATION_JSON));
        curseForge.expect(requestTo("https://edge.forgecdn.net/files/1/11/ATM10%20Server%205.0.zip"))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM).header("Content-Length", "3"));

        Path target = dir.resolve("pack.zip");
        long[] last = new long[2];
        client.download(1, 11, target, (done, total) -> {
            last[0] = done;
            last[1] = total;
        });

        assertThat(Files.readAllBytes(target)).containsExactly(1, 2, 3);
        assertThat(last).containsExactly(3, 3); // every byte counted, against the size the CDN sent
    }

    @Test
    void aRefusalIsWordedForAnAdminAndNeverCarriesTheKey() {
        curseForge.expect(requestTo(CurseForgeClient.API + "/v1/mods/1/files/11/download-url"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.download(1, 11, dir.resolve("pack.zip"), TransferProgress.NONE))
                .isInstanceOf(CurseForgeClient.CurseForgeException.class)
                .hasMessageContaining("CurseForge app")
                .message().doesNotContain(KEY);
    }

    @Test
    void withoutAKeyNothingCallsOut() {
        var unconfigured = new CurseForgeClient(new LodestoneProperties.CurseForge(""), builder);

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.searchModpacks("x")).hasMessageContaining("isn't configured");
        curseForge.verify(); // no requests expected, none made
    }
}
