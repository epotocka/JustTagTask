package com.example.justtagtask.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.example.justtagtask.IntegrationTest;
import com.example.justtagtask.TestCsv;
import org.junit.jupiter.api.Test;

class CanonicalLoadStopRestartIntegrationTest extends IntegrationTest {

    private static final int ROWS = 2_000;

    @Test
    void stopsALoadAndResumesItWithoutMissingOrDuplicateRows() {
        UUID fileId = uploadReady(TestCsv.generated(ROWS));
        restTestClient.post().uri("/api/files/{id}/load", fileId).exchange().expectStatus().isAccepted();

        // Spring Batch 6.0.5 race in JobOperator.stop(), which can end a stopped load as FAILED instead of STOPPED:
        // 1. The request thread saves the job as STOPPED, then saves its own copy of the running step, bumping the step's version.
        // 2. The job thread read the step's previous version just before, so its next save fails with OptimisticLockingFailureException.
        // 3. The step fails, the job ends FAILED, and the listener marks the file FAILED.
        // When the request thread loses the race instead, the stop endpoint returns 500, although the job still stops.
        // Both FAILED and STOPPED loads can be restarted, so the test accepts either outcome.
        restTestClient.post().uri("/api/files/{id}/load/stop", fileId).exchange()
                .expectStatus().value(status -> assertThat(status).isIn(202, 500));
        awaitFileStatus(fileId, "STOPPED", "FAILED");
        assertThat(canonicalRowCount(fileId)).isLessThan(ROWS);

        restTestClient.post().uri("/api/files/{id}/load/restart", fileId).exchange().expectStatus().isAccepted();
        awaitFileStatus(fileId, "LOADED");

        assertThat(canonicalRowCount(fileId)).isEqualTo(ROWS);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT min(row_number) = 1 AND max(row_number) = ? FROM canonical.ad_record WHERE file_id = ?",
                Boolean.class, ROWS, fileId)).isTrue();
        assertThat(get("/api/files/{id}/load", fileId).get("status").asString()).isEqualTo("COMPLETED");
    }

    @Test
    void refusesToRestartALoadThatIsNotStoppedOrFailed() {
        UUID fileId = uploadReady(TestCsv.generated(ROWS));
        restTestClient.post().uri("/api/files/{id}/load", fileId).exchange().expectStatus().isAccepted();

        restTestClient.post().uri("/api/files/{id}/load/restart", fileId).exchange().expectStatus().isEqualTo(409);

        awaitFileStatus(fileId, "LOADED");
    }

    private long canonicalRowCount(UUID fileId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM canonical.ad_record WHERE file_id = ?", Long.class, fileId);
    }

}
