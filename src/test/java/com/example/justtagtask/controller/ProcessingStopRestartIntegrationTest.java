package com.example.justtagtask.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import com.example.justtagtask.IntegrationTest;
import com.example.justtagtask.TestCsv;
import com.example.justtagtask.model.processing.ResultTable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class ProcessingStopRestartIntegrationTest extends IntegrationTest {

    private static final int ROWS = 2_000;
    private static final String DIVIDE_CLICKS_BY_IMPRESSIONS = """
            {"type": "OPERATION", "leftColumn": "clicks", "rightColumn": "impressions", "operation": "DIVIDE"}
            """;
    private static final String SUM_CLICKS_PER_CAMPAIGN = """
            {"type": "AGGREGATION", "groupBy": "campaignId", "valueColumn": "clicks", "function": "SUM"}
            """;
    private static final String STATEMENTS_WAITING_FOR_A_LOCK = """
            FROM pg_stat_activity
            WHERE wait_event_type = 'Lock' AND position(? IN query) > 0
            """;

    @Autowired
    private DataSource dataSource;

    @Test
    void stopsARunAndResumesItWithoutMissingOrDuplicateResults() {
        UUID fileId = uploadLoaded(TestCsv.generated(ROWS));
        UUID runId = startRun(fileId, DIVIDE_CLICKS_BY_IMPRESSIONS);

        // Same Spring Batch race in JobOperator.stop() as in CanonicalLoadStopRestartIntegrationTest: a stopped run can end FAILED, or the stop can return 500.
        restTestClient.post().uri("/api/files/{fileId}/process/{runId}/stop", fileId, runId).exchange()
                .expectStatus().value(status -> assertThat(status).isIn(202, 500));
        awaitRunStatus(fileId, runId, "STOPPED", "FAILED");
        String resultTable = new ResultTable(runId).name();

        restTestClient.post().uri("/api/files/{fileId}/process/{runId}/restart", fileId, runId).exchange().expectStatus().isAccepted();
        awaitRunStatus(fileId, runId, "COMPLETED");

        assertThat(jdbcTemplate.queryForObject("SELECT count(DISTINCT row_number) FROM " + resultTable, Long.class)).isEqualTo(ROWS);
        assertThat(run(fileId, runId).get("execution").get("status").asString()).isEqualTo("COMPLETED");
    }

    @Test
    void refusesToStartASecondRunWhileOneIsInProgress() {
        UUID fileId = uploadLoaded(TestCsv.generated(ROWS));
        UUID runId = startRun(fileId, DIVIDE_CLICKS_BY_IMPRESSIONS);

        restTestClient.post().uri("/api/files/{id}/process", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(DIVIDE_CLICKS_BY_IMPRESSIONS)
                .exchange()
                .expectStatus().isEqualTo(409);

        awaitRunStatus(fileId, runId, "COMPLETED");
    }

    @Test
    void stopsAnAggregationByCancellingItsRunningStatement() throws SQLException {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);
        try (Connection lock = lockAdRecords()) {
            UUID runId = startRun(fileId, SUM_CLICKS_PER_CAMPAIGN);
            awaitStatementWaitingForLock(runId);

            restTestClient.post().uri("/api/files/{fileId}/process/{runId}/stop", fileId, runId).exchange()
                    .expectStatus().value(status -> assertThat(status).isIn(202, 500));
            awaitRunStatus(fileId, runId, "STOPPED", "FAILED");

            assertThat(resultRowCount(runId)).isZero();
        }
    }

    @Test
    void failsAnAggregationWhoseStatementBreaksAndRestartsIt() throws SQLException {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);
        UUID runId;
        try (Connection lock = lockAdRecords()) {
            runId = startRun(fileId, SUM_CLICKS_PER_CAMPAIGN);
            awaitStatementWaitingForLock(runId);

            jdbcTemplate.queryForList("SELECT pg_cancel_backend(pid) " + STATEMENTS_WAITING_FOR_A_LOCK, new ResultTable(runId).name());
            awaitRunStatus(fileId, runId, "FAILED");
        }
        assertThat(run(fileId, runId).get("execution").get("exitMessage").asString()).contains("canceling statement");

        restTestClient.post().uri("/api/files/{fileId}/process/{runId}/restart", fileId, runId).exchange().expectStatus().isAccepted();
        awaitRunStatus(fileId, runId, "COMPLETED");
        assertThat(resultRowCount(runId)).isEqualTo(campaignCount(fileId));
    }

    @Test
    void reportsNoProgressPercentageForAnAggregationInProgress() throws SQLException {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);
        UUID runId;
        try (Connection lock = lockAdRecords()) {
            runId = startRun(fileId, SUM_CLICKS_PER_CAMPAIGN);
            awaitStatementWaitingForLock(runId);

            assertThat(run(fileId, runId).get("status").asString()).isEqualTo("STARTED");
            assertThat(run(fileId, runId).get("execution").get("progressPercent").isNull()).isTrue();
        }

        awaitRunStatus(fileId, runId, "COMPLETED");
    }

    private Connection lockAdRecords() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("LOCK TABLE canonical.ad_record IN ACCESS EXCLUSIVE MODE");
        }
        return connection;
    }

    private void awaitStatementWaitingForLock(UUID runId) {
        await().atMost(TIMEOUT).until(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) " + STATEMENTS_WAITING_FOR_A_LOCK, Long.class, new ResultTable(runId).name()) == 1);
    }

    private long resultRowCount(UUID runId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + new ResultTable(runId).name(), Long.class);
    }

    private long campaignCount(UUID fileId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT campaign_id) FROM canonical.ad_record WHERE file_id = ?", Long.class, fileId);
    }

}
