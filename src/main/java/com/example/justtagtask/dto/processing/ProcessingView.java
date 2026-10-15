package com.example.justtagtask.dto.processing;

import java.time.Instant;
import java.util.UUID;

import com.example.justtagtask.dto.common.ExecutionView;
import com.example.justtagtask.model.processing.ProcessingRun;
import com.example.justtagtask.model.processing.RunDefinition;
import com.example.justtagtask.model.processing.enums.RunStatus;
import com.example.justtagtask.model.processing.enums.RunType;

public record ProcessingView(
        UUID runId,
        UUID fileId,
        RunType type,
        RunDefinition settings,
        RunStatus status,
        String resultTable,
        Instant createdAt,
        ExecutionView execution) {

    public static ProcessingView of(ProcessingRun run, ExecutionView execution) {
        return new ProcessingView(
                run.id(),
                run.fileId(),
                run.type(),
                run.definition(),
                run.status(),
                run.resultTable(),
                run.createdAt(),
                execution);
    }

}
