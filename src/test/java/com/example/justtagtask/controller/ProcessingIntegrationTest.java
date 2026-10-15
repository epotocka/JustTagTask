package com.example.justtagtask.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.justtagtask.IntegrationTest;
import com.example.justtagtask.TestCsv;
import com.example.justtagtask.model.canonical.enums.AdColumn;
import com.example.justtagtask.model.processing.ProcessingRun;
import com.example.justtagtask.model.processing.ResultTable;
import com.example.justtagtask.model.processing.RowOperation;
import com.example.justtagtask.model.processing.enums.Operation;
import com.example.justtagtask.service.processing.InterruptedProcessingRecovery;
import com.example.justtagtask.service.processing.ProcessingJobParameters;
import com.example.justtagtask.service.processing.ProcessingRunCreator;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

class ProcessingIntegrationTest extends IntegrationTest {

    private static final String MULTIPLY_REVENUE_BY_MARGIN = """
            {"type": "OPERATION", "leftColumn": "revenue", "rightColumn": "advertiserMargin", "operation": "MULTIPLY"}
            """;
    private static final String DIVIDE_CLICKS_BY_IMPRESSIONS = """
            {"type": "OPERATION", "leftColumn": "clicks", "rightColumn": "impressions", "operation": "DIVIDE"}
            """;
    private static final String SUM_REVENUE_PER_CAMPAIGN = """
            {"type": "AGGREGATION", "groupBy": "campaignId", "valueColumn": "revenue", "function": "SUM"}
            """;

    @Autowired
    private ProcessingRunCreator processingRunCreator;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private InterruptedProcessingRecovery interruptedProcessingRecovery;

    private final List<String[]> sampleRows = TestCsv.dataLines(TestCsv.SAMPLE).stream()
            .map(line -> line.split(AdColumn.CSV_DELIMITER))
            .toList();

    @Test
    void writesTheResultOfARowOperationForEveryRow() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);

        Map<Long, BigDecimal> results = rowResults(completedRun(fileId, MULTIPLY_REVENUE_BY_MARGIN));

        assertThat(results).hasSize(sampleRows.size());
        for (int index = 0; index < sampleRows.size(); index++) {
            String[] row = sampleRows.get(index);
            BigDecimal expected = value(row, AdColumn.REVENUE).multiply(value(row, AdColumn.ADVERTISER_MARGIN));
            assertThat(results.get(index + 1L)).isEqualByComparingTo(expected);
        }
    }

    @Test
    void writesNoResultWhenDividingByZero() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);

        Map<Long, BigDecimal> results = rowResults(completedRun(fileId, DIVIDE_CLICKS_BY_IMPRESSIONS));

        for (int index = 0; index < sampleRows.size(); index++) {
            String[] row = sampleRows.get(index);
            BigDecimal impressions = value(row, AdColumn.IMPRESSIONS);
            BigDecimal result = results.get(index + 1L);
            if (impressions.signum() == 0) {
                assertThat(result).isNull();
            } else {
                assertThat(result).isEqualByComparingTo(
                        value(row, AdColumn.CLICKS).divide(impressions, 10, RoundingMode.HALF_UP));
            }
        }
    }

    @Test
    void writesOneResultPerGroupForAnAggregation() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);
        Map<Long, BigDecimal> expected = new HashMap<>();
        for (String[] row : sampleRows) {
            expected.merge(value(row, AdColumn.CAMPAIGN_ID).longValueExact(), value(row, AdColumn.REVENUE), BigDecimal::add);
        }

        JsonNode run = completedRun(fileId, SUM_REVENUE_PER_CAMPAIGN);

        Map<Long, BigDecimal> actual = new HashMap<>();
        jdbcTemplate.query("SELECT group_value, result FROM " + resultTableOf(run),
                resultSet -> {
                    actual.put(resultSet.getLong("group_value"), resultSet.getBigDecimal("result"));
                });
        assertThat(actual).hasSameSizeAs(expected);
        expected.forEach((campaign, sum) -> assertThat(actual.get(campaign)).isEqualByComparingTo(sum));
    }

    @Test
    void keepsEachRunInItsOwnTableAndLeavesTheCanonicalDataUnchanged() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);
        Map<String, Object> canonicalBefore = canonicalFingerprint(fileId);

        JsonNode first = completedRun(fileId, MULTIPLY_REVENUE_BY_MARGIN);
        JsonNode second = completedRun(fileId, SUM_REVENUE_PER_CAMPAIGN);

        assertThat(first.get("resultTable").asString()).isNotEqualTo(second.get("resultTable").asString());
        assertThat(rowResults(first)).hasSize(1000);
        assertThat(canonicalFingerprint(fileId)).isEqualTo(canonicalBefore);
    }

    @Test
    void reportsTheProgressOfARun() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);

        JsonNode run = completedRun(fileId, MULTIPLY_REVENUE_BY_MARGIN);

        assertThat(run.get("type").asString()).isEqualTo("OPERATION");
        assertThat(run.get("settings").get("leftColumn").asString()).isEqualTo("revenue");
        JsonNode execution = run.get("execution");
        assertThat(execution.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(execution.get("readCount").asLong()).isEqualTo(1000);
        assertThat(execution.get("writeCount").asLong()).isEqualTo(1000);
        assertThat(execution.get("progressPercent").asDouble()).isEqualTo(100.0);
    }

    @Test
    void refusesToProcessAFileThatIsNotLoaded() {
        UUID fileId = uploadReady(TestCsv.SAMPLE);

        postRun(fileId, MULTIPLY_REVENUE_BY_MARGIN).expectStatus().isEqualTo(409);
    }

    @Test
    void rejectsANonNumericOperand() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);

        postRun(fileId, """
                {"type": "OPERATION", "leftColumn": "advertiserId", "rightColumn": "clicks", "operation": "ADD"}
                """)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail")
                .isEqualTo("leftColumn must be one of: impressions, clicks, revenue, advertiserMargin");
    }

    @Test
    void rejectsAnUnknownRunType() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);

        postRun(fileId, """
                {"type": "PIVOT", "groupBy": "campaignId"}
                """).expectStatus().isBadRequest();
    }

    @Test
    void returnsNotFoundForARunOfAnotherFile() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);
        UUID runId = UUID.fromString(completedRun(fileId, MULTIPLY_REVENUE_BY_MARGIN).get("runId").asString());

        restTestClient.get().uri("/api/files/{fileId}/process/{runId}", UUID.randomUUID(), runId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void returnsNotFoundWhenProcessingAnUnknownFile() {
        postRun(UUID.randomUUID(), MULTIPLY_REVENUE_BY_MARGIN).expectStatus().isNotFound();
    }

    @Test
    void refusesToRestartACompletedRun() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);
        UUID runId = UUID.fromString(completedRun(fileId, MULTIPLY_REVENUE_BY_MARGIN).get("runId").asString());

        restTestClient.post().uri("/api/files/{fileId}/process/{runId}/restart", fileId, runId)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void reportsARunWhoseJobHasNotStarted() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);
        ProcessingRun run = processingRunCreator.create(fileId, new RowOperation(AdColumn.REVENUE, AdColumn.CLICKS, Operation.ADD));

        JsonNode view = run(fileId, run.id());

        assertThat(view.get("status").asString()).isEqualTo("STARTING");
        assertThat(view.get("execution").isNull()).isTrue();
        restTestClient.post().uri("/api/files/{fileId}/process/{runId}/stop", fileId, run.id())
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.detail").isEqualTo("Run " + run.id() + " hasn't started");
    }

    @Test
    void failsRunsLeftRunningByAServerStop() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);
        ProcessingRun run = processingRunCreator.create(fileId, new RowOperation(AdColumn.REVENUE, AdColumn.CLICKS, Operation.ADD));
        JobParameters parameters = ProcessingJobParameters.forRun(run.id());
        JobInstance instance = jobRepository.createJobInstance(ProcessingJobParameters.ROW_OPERATION_JOB, parameters);
        jobRepository.createJobExecution(instance, parameters, new ExecutionContext());

        interruptedProcessingRecovery.run(new DefaultApplicationArguments());

        JsonNode recovered = run(fileId, run.id());
        assertThat(recovered.get("status").asString()).isEqualTo("FAILED");
        assertThat(recovered.get("execution").get("status").asString()).isEqualTo("FAILED");
    }

    private JsonNode completedRun(UUID fileId, String request) {
        UUID runId = startRun(fileId, request);
        awaitRunStatus(fileId, runId, "COMPLETED");
        return run(fileId, runId);
    }

    private RestTestClient.ResponseSpec postRun(UUID fileId, String request) {
        return restTestClient.post().uri("/api/files/{id}/process", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private Map<Long, BigDecimal> rowResults(JsonNode run) {
        Map<Long, BigDecimal> results = new HashMap<>();
        jdbcTemplate.query("SELECT row_number, result FROM " + resultTableOf(run),
                resultSet -> {
                    results.put(resultSet.getLong("row_number"), resultSet.getBigDecimal("result"));
                });
        return results;
    }

    private Map<String, Object> canonicalFingerprint(UUID fileId) {
        return jdbcTemplate.queryForMap("""
                SELECT count(*) AS rows, sum(revenue) AS revenue, sum(advertiser_margin) AS margin, sum(clicks) AS clicks
                FROM canonical.ad_record
                WHERE file_id = ?
                """, fileId);
    }

    private static String resultTableOf(JsonNode run) {
        return new ResultTable(UUID.fromString(run.get("runId").asString())).name();
    }

    private static BigDecimal value(String[] row, AdColumn column) {
        return new BigDecimal(row[column.ordinal()]);
    }

}
