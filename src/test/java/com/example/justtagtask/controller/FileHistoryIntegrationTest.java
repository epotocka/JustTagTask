package com.example.justtagtask.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.example.justtagtask.IntegrationTest;
import com.example.justtagtask.TestCsv;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class FileHistoryIntegrationTest extends IntegrationTest {

    @Test
    void listsFilesNewestFirstWithAllTheirRuns() {
        UUID processedFile = uploadLoaded(TestCsv.SAMPLE);
        UUID firstRunId = startRun(processedFile, """
                {"type": "AGGREGATION", "groupBy": "campaignId", "valueColumn": "clicks", "function": "MAX"}
                """);
        awaitRunStatus(processedFile, firstRunId, "COMPLETED");
        UUID secondRunId = startRun(processedFile, """
                {"type": "OPERATION", "leftColumn": "clicks", "rightColumn": "impressions", "operation": "DIVIDE"}
                """);
        awaitRunStatus(processedFile, secondRunId, "COMPLETED");
        UUID newerFile = uploadReady(TestCsv.SAMPLE);

        JsonNode page = get("/api/files?size=100");

        List<String> ids = page.get("content").valueStream().map(file -> file.get("fileId").asString()).toList();
        assertThat(ids).containsSubsequence(newerFile.toString(), processedFile.toString());
        JsonNode processed = page.get("content").valueStream()
                .filter(file -> file.get("fileId").asString().equals(processedFile.toString()))
                .findFirst()
                .orElseThrow();
        assertThat(processed.get("status").asString()).isEqualTo("LOADED");
        assertThat(processed.get("runs").valueStream().map(run -> run.get("runId").asString()))
                .containsExactly(secondRunId.toString(), firstRunId.toString());
        assertThat(processed.get("runs").valueStream().map(run -> run.get("status").asString()))
                .containsOnly("COMPLETED");
        JsonNode newer = page.get("content").valueStream()
                .filter(file -> file.get("fileId").asString().equals(newerFile.toString()))
                .findFirst()
                .orElseThrow();
        assertThat(newer.get("runs")).isEmpty();
    }

    @Test
    void filtersByStatusAndPages() {
        UUID failedFile = upload(TestCsv.withLines("wrong;header", List.of("1;2")));
        awaitFileStatus(failedFile, "FAILED");
        upload(TestCsv.withLines("another;wrong;header", List.of("1;2;3")));

        JsonNode firstPage = get("/api/files?status=FAILED&size=1&page=0");

        assertThat(firstPage.get("size").asInt()).isEqualTo(1);
        assertThat(firstPage.get("content")).hasSize(1);
        assertThat(firstPage.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode allFailed = get("/api/files?status=FAILED&size=100");
        assertThat(allFailed.get("content").valueStream().map(file -> file.get("status").asString()))
                .containsOnly("FAILED");
        assertThat(allFailed.get("content").valueStream().map(file -> file.get("fileId").asString()))
                .contains(failedFile.toString());
    }

    @Test
    void limitsThePageSize() {
        JsonNode page = get("/api/files?size=1000");

        assertThat(page.get("size").asInt()).isEqualTo(100);
    }

    @Test
    void returnsAnEmptyPageBeyondTheLastFile() {
        JsonNode page = get("/api/files?page=100000");

        assertThat(page.get("content")).isEmpty();
        assertThat(page.get("page").asInt()).isEqualTo(100000);
    }

    @Test
    void showsTheDetailsOfAFileWithItsLoadAndRuns() {
        UUID fileId = uploadLoaded(TestCsv.SAMPLE);
        UUID runId = startRun(fileId, """
                {"type": "OPERATION", "leftColumn": "revenue", "rightColumn": "clicks", "operation": "SUBTRACT"}
                """);
        awaitRunStatus(fileId, runId, "COMPLETED");

        JsonNode details = file(fileId);

        assertThat(details.get("status").asString()).isEqualTo("LOADED");
        assertThat(details.get("load").get("status").asString()).isEqualTo("COMPLETED");
        assertThat(details.get("runs")).hasSize(1);
        assertThat(details.get("runs").get(0).get("settings").get("operation").asString()).isEqualTo("SUBTRACT");
    }

    @Test
    void returnsNotFoundForAnUnknownFile() {
        restTestClient.get().uri("/api/files/{id}", UUID.randomUUID()).exchange().expectStatus().isNotFound();
    }

}
