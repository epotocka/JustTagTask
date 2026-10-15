package com.example.justtagtask.dto.processing;

import java.time.Instant;
import java.util.UUID;

import com.example.justtagtask.model.processing.ProcessingRun;
import com.example.justtagtask.model.processing.RunDefinition;
import com.example.justtagtask.model.processing.enums.RunStatus;
import com.example.justtagtask.model.processing.enums.RunType;

public record ProcessingSummary(
        UUID runId,
        RunType type,
        RunDefinition settings,
        RunStatus status,
        String resultTable,
        Instant createdAt) {

    public static ProcessingSummary of(ProcessingRun run) {
        return new ProcessingSummary(run.id(), run.type(), run.definition(), run.status(), run.resultTable(), run.createdAt());
    }

}
