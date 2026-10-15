package com.example.justtagtask.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.justtagtask.IntegrationTest;
import com.example.justtagtask.TestCsv;
import com.example.justtagtask.config.common.IngestionProperties;
import com.example.justtagtask.model.canonical.enums.AdColumn;
import com.example.justtagtask.service.loading.CanonicalLoadJobParameters;
import com.example.justtagtask.service.loading.InterruptedCanonicalLoadRecovery;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import tools.jackson.databind.JsonNode;

class CanonicalLoadIntegrationTest extends IntegrationTest {

    @Autowired
    private IngestionProperties ingestionProperties;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private InterruptedCanonicalLoadRecovery interruptedCanonicalLoadRecovery;

    @Test
    void loadsTheSampleFileIntoTheCanonicalSchema() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);

        assertThat(canonicalRowCount(fileId)).isEqualTo(1000);
        Map<String, Object> firstRow = jdbcTemplate.queryForMap(
                "SELECT * FROM canonical.ad_record WHERE file_id = ? AND row_number = 1", fileId);
        assertThat(firstRow)
                .containsEntry("advertiser_id", 5342L)
                .containsEntry("campaign_id", 11380L)
                .containsEntry("ad_box_uuid", UUID.fromString("287d06ca-6f4c-469a-8b22-d3081c8eaee9"))
                .containsEntry("impressions", 591L)
                .containsEntry("clicks", 22L)
                .containsEntry("revenue", new BigDecimal("2.9798"))
                .containsEntry("advertiser_margin", new BigDecimal("0.1927"));
    }

    @Test
    void reportsTheCompletedLoad() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);

        JsonNode load = get("/api/files/{id}/load", fileId);

        assertThat(load.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(load.get("readCount").asLong()).isEqualTo(1000);
        assertThat(load.get("writeCount").asLong()).isEqualTo(1000);
        assertThat(load.get("skipCount").asLong()).isZero();
        assertThat(load.get("progressPercent").asDouble()).isEqualTo(100.0);
    }

    @Test
    void skipsMalformedLinesAndKeepsTheRowNumbersOfValidOnes() {
        List<String> sampleLines = TestCsv.dataLines(TestCsv.SAMPLE);
        Path csv = TestCsv.withLines(AdColumn.csvHeader(), List.of(
                sampleLines.get(0),
                "not;enough;columns",
                sampleLines.get(1),
                "1;2;3;not-a-uuid;4;5;6.0;0.1",
                ";;;;;;;",
                "",
                "# " + sampleLines.get(2),
                "5342;null;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927",
                "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;1234567890123456.0;0.1927",
                sampleLines.get(3)));

        UUID fileId = uploadLoaded(csv);

        assertThat(jdbcTemplate.queryForList(
                "SELECT row_number FROM canonical.ad_record WHERE file_id = ? ORDER BY row_number", Long.class, fileId))
                .containsExactly(1L, 3L, 10L);
        assertThat(get("/api/files/{id}/load", fileId).get("skipCount").asLong()).isEqualTo(7);
    }

    @Test
    void failsALoadWithMoreMalformedLinesThanTheSkipLimit() {
        UUID fileId = uploadReady(TestCsv.withLines(AdColumn.csvHeader(), Collections.nCopies(ingestionProperties.skipLimit() + 1, "malformed")));

        restTestClient.post().uri("/api/files/{id}/load", fileId).exchange().expectStatus().isAccepted();
        awaitFileStatus(fileId, "FAILED");

        assertThat(file(fileId).get("failureReason").asString())
                .contains("SkipLimitExceededException")
                .hasSizeLessThanOrEqualTo(2500);
        assertThat(get("/api/files/{id}/load", fileId).get("status").asString()).isEqualTo("FAILED");
    }

    @Test
    void refusesToStopALoadThatHasFinished() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);

        restTestClient.post().uri("/api/files/{id}/load/stop", fileId).exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.detail").isEqualTo("The job isn't running");
    }

    @Test
    void failsLoadsLeftRunningByAServerStop() {
        UUID fileId = uploadReady(TestCsv.SAMPLE);
        JobParameters parameters = CanonicalLoadJobParameters.forFile(fileId);
        JobInstance instance = jobRepository.createJobInstance(CanonicalLoadJobParameters.JOB_NAME, parameters);
        jobRepository.createJobExecution(instance, parameters, new ExecutionContext());

        interruptedCanonicalLoadRecovery.run(new DefaultApplicationArguments());

        assertThat(file(fileId).get("failureReason").asString()).isEqualTo("The server stopped during the load");
        assertThat(file(fileId).get("status").asString()).isEqualTo("FAILED");
        assertThat(get("/api/files/{id}/load", fileId).get("status").asString()).isEqualTo("FAILED");
    }

    @Test
    void refusesToLoadAFileThatIsNotReady() {
        UUID fileId = upload(TestCsv.withLines("a;b", List.of("1;2")));
        awaitFileStatus(fileId, "FAILED");

        restTestClient.post().uri("/api/files/{id}/load", fileId).exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void refusesToLoadTheSameFileTwice() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);

        restTestClient.post().uri("/api/files/{id}/load", fileId).exchange().expectStatus().isEqualTo(409);
        assertThat(canonicalRowCount(fileId)).isEqualTo(1000);
    }

    @Test
    void reportsThatTheLoadHasNotStarted() {
        UUID fileId = uploadReady(TestCsv.SAMPLE);

        restTestClient.get().uri("/api/files/{id}/load", fileId).exchange().expectStatus().isNotFound();
        restTestClient.post().uri("/api/files/{id}/load/stop", fileId).exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void returnsNotFoundForAnUnknownFile() {
        restTestClient.post().uri("/api/files/{id}/load", UUID.randomUUID()).exchange().expectStatus().isNotFound();
    }

    private long canonicalRowCount(UUID fileId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM canonical.ad_record WHERE file_id = ?", Long.class, fileId);
    }

}
