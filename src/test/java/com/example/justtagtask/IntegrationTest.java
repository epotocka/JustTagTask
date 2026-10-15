package com.example.justtagtask;

import static org.awaitility.Awaitility.await;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ingestion.storage-directory=target/test-uploads",
                "ingestion.max-file-size=" + IntegrationTest.MAX_FILE_SIZE_BYTES + "B",
                "ingestion.progress-save-interval=0s",
                "ingestion.chunk-size=5"
        })
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final Duration TIMEOUT = Duration.ofSeconds(5);
    protected static final long MAX_FILE_SIZE_BYTES = 200 * 1024;

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected RestTestClient restTestClient;

    @BeforeEach
    void createClient() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    protected RestTestClient.ResponseSpec postFile(Path csv) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new FileSystemResource(csv));
        return restTestClient.post().uri("/api/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .exchange();
    }

    protected UUID upload(Path csv) {
        JsonNode body = postFile(csv)
                .expectStatus().isAccepted()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        return UUID.fromString(body.get("fileId").asString());
    }

    protected UUID uploadReady(Path csv) {
        UUID fileId = upload(csv);
        awaitFileStatus(fileId, "READY");
        return fileId;
    }

    protected UUID uploadLoaded(Path csv) {
        UUID fileId = uploadReady(csv);
        restTestClient.post().uri("/api/files/{id}/load", fileId).exchange().expectStatus().isAccepted();
        awaitFileStatus(fileId, "LOADED");
        return fileId;
    }

    protected UUID startRun(UUID fileId, String request) {
        JsonNode body = restTestClient.post().uri("/api/files/{id}/process", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        return UUID.fromString(body.get("runId").asString());
    }

    protected JsonNode get(String uri, Object... variables) {
        return restTestClient.get().uri(uri, variables)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
    }

    protected JsonNode file(UUID fileId) {
        return get("/api/files/{id}", fileId);
    }

    protected JsonNode run(UUID fileId, UUID runId) {
        return get("/api/files/{fileId}/process/{runId}", fileId, runId);
    }

    protected void awaitFileStatus(UUID fileId, String... statuses) {
        await().atMost(TIMEOUT).until(() -> List.of(statuses).contains(file(fileId).get("status").asString()));
    }

    protected void awaitRunStatus(UUID fileId, UUID runId, String... statuses) {
        await().atMost(TIMEOUT).until(() -> List.of(statuses).contains(run(fileId, runId).get("status").asString()));
    }

}
