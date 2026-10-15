package com.example.justtagtask.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.example.justtagtask.IntegrationTest;
import com.example.justtagtask.TestCsv;
import com.example.justtagtask.model.canonical.enums.AdColumn;
import com.example.justtagtask.service.upload.FileStorage;
import com.example.justtagtask.service.upload.InterruptedUploadRecovery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

class UploadIntegrationTest extends IntegrationTest {

    private static final String BOUNDARY = "test-boundary";

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private InterruptedUploadRecovery interruptedUploadRecovery;

    @Test
    void uploadsTheSampleFileAndMarksItReadyAfterTheHeaderCheck() throws IOException {
        UUID fileId = uploadReady(TestCsv.SAMPLE);

        JsonNode file = file(fileId);
        assertThat(file.get("originalFilename").asString()).isEqualTo("sample_ads_data.csv");
        assertThat(file.get("rowCount").asLong()).isEqualTo(1000);
        assertThat(file.get("bytesReceived").asLong()).isEqualTo(Files.size(TestCsv.SAMPLE));
        assertThat(fileStorage.pathFor(fileId)).hasSameBinaryContentAs(TestCsv.SAMPLE);
    }

    @Test
    void respondsWithTheFileLocation() {
        postFile(TestCsv.SAMPLE)
                .expectStatus().isAccepted()
                .expectHeader().valueMatches("Location", ".*/api/files/[0-9a-f-]{36}$")
                .expectBody().jsonPath("$.status").isEqualTo("UPLOADED");
    }

    @Test
    void marksAFileWithAnUnexpectedHeaderAsFailed() {
        Path csv = TestCsv.withLines("a;b;c", List.of("1;2;3"));

        UUID fileId = upload(csv);

        awaitFileStatus(fileId, "FAILED");
        assertThat(file(fileId).get("failureReason").asString()).contains("Expected the header");
    }

    @Test
    void acceptsAHeaderWithWhitespaceAroundColumnNames() {
        String spacedHeader = String.join(" ; ", AdColumn.csvHeader().split(AdColumn.CSV_DELIMITER));

        UUID fileId = upload(TestCsv.withLines(spacedHeader, TestCsv.dataLines(TestCsv.SAMPLE)));

        awaitFileStatus(fileId, "READY");
    }

    @Test
    void marksABinaryFileWithoutLineBreaksAsFailed() {
        Path binary = TestCsv.withLines("\u0000ÿ".repeat(5_000), List.of());

        UUID fileId = upload(binary);

        awaitFileStatus(fileId, "FAILED");
    }

    @Test
    void rejectsARequestWithoutAFilePart() {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("notes", "no file here");
        parts.add("attachment", new FileSystemResource(TestCsv.SAMPLE));

        restTestClient.post().uri("/api/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").isEqualTo("The request has no 'file' part");
    }

    @Test
    void rejectsARequestThatIsNotMultipart() {
        restTestClient.post().uri("/api/files")
                .contentType(MediaType.TEXT_PLAIN)
                .body("advertiserId;campaignId")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").isEqualTo("Expected a multipart/form-data request");
    }

    @Test
    void rejectsAMultipartRequestWithoutABoundary() {
        restTestClient.post().uri("/api/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body("advertiserId;campaignId")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail")
                .value(String.class, detail -> assertThat(detail).startsWith("Could not read the multipart request"));
    }

    @Test
    void namesAFileUploadedWithoutAFilenameUnnamed() throws IOException {
        byte[] body = (multipartFileHeader("") + Files.readString(TestCsv.SAMPLE) + "\r\n--" + BOUNDARY + "--\r\n")
                .getBytes(StandardCharsets.UTF_8);

        JsonNode response = restTestClient.post().uri("/api/files")
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + BOUNDARY))
                .body(body)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();

        assertThat(file(UUID.fromString(response.get("fileId").asString())).get("originalFilename").asString()).isEqualTo("unnamed.csv");
    }

    @Test
    void savesTheProgressOfAnUploadThatIsStillRunning() throws IOException {
        String filename = UUID.randomUUID() + ".csv";

        sendPartialUploadAndDisconnect(filename, () -> jdbcTemplate.queryForList(
                "SELECT bytes_received FROM uploaded_file WHERE original_filename = ? AND status = 'UPLOADING'",
                Long.class, filename).stream().anyMatch(bytes -> bytes > 0));
    }

    @Test
    void marksAnUploadAbortedByTheClientAsFailedAndDeletesThePartialFile() throws IOException {
        String filename = UUID.randomUUID() + ".csv";
        sendPartialUploadAndDisconnect(filename, () -> !statusOfUpload(filename).isEmpty());

        await().atMost(TIMEOUT).until(() -> statusOfUpload(filename).equals("FAILED"));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, failure_reason FROM uploaded_file WHERE original_filename = ?", filename);
        assertThat((String) row.get("failure_reason")).startsWith("The upload was interrupted");
        assertThat(fileStorage.pathFor((UUID) row.get("id"))).doesNotExist();
    }

    @Test
    void failsUploadsLeftUnfinishedByAServerStop() throws IOException {
        UUID fileId = UUID.randomUUID();
        Path partialFile = Files.writeString(fileStorage.pathFor(fileId), "advertiserId;campaignId");
        jdbcTemplate.update("""
                INSERT INTO uploaded_file (id, original_filename, storage_path, status)
                VALUES (?, 'interrupted.csv', ?, 'UPLOADING')
                """, fileId, partialFile.toString());

        interruptedUploadRecovery.recover();

        JsonNode file = file(fileId);
        assertThat(file.get("status").asString()).isEqualTo("FAILED");
        assertThat(file.get("failureReason").asString()).isEqualTo("The server stopped during the upload");
        assertThat(partialFile).doesNotExist();
    }

    private void sendPartialUploadAndDisconnect(String filename, Callable<Boolean> disconnectWhen) throws IOException {
        byte[] partialBody = (multipartFileHeader(filename) + Files.readString(TestCsv.SAMPLE).substring(0, 5_000))
                .getBytes(StandardCharsets.UTF_8);
        String requestHead = "POST /api/files HTTP/1.1\r\n"
                + "Host: localhost:" + port + "\r\n"
                + "Content-Type: multipart/form-data; boundary=" + BOUNDARY + "\r\n"
                + "Content-Length: " + (partialBody.length + 100_000) + "\r\n\r\n";
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(requestHead.getBytes(StandardCharsets.US_ASCII));
            out.write(partialBody);
            out.flush();
            await().atMost(TIMEOUT).until(disconnectWhen);
        }
    }

    private static String multipartFileHeader(String filename) {
        return "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: text/csv\r\n\r\n";
    }

    private String statusOfUpload(String filename) {
        return jdbcTemplate.queryForList("SELECT status FROM uploaded_file WHERE original_filename = ?", String.class, filename)
                .stream()
                .findFirst()
                .orElse("");
    }

}
