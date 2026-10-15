package com.example.justtagtask.model.processing;

import java.time.Instant;
import java.util.UUID;

import com.example.justtagtask.model.processing.enums.RunStatus;
import com.example.justtagtask.model.processing.enums.RunType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Table("processing_run")
public record ProcessingRun(
        @Id UUID id,
        UUID fileId,
        RunType type,
        @Embedded.Nullable RowOperation rowOperation,
        @Embedded.Nullable Aggregation aggregation,
        RunStatus status,
        String resultTable,
        Instant createdAt,
        Instant updatedAt) {

    public static ProcessingRun starting(UUID id, UUID fileId, RunDefinition definition, String resultTable) {
        Instant now = Instant.now();
        return new ProcessingRun(
                id,
                fileId,
                definition.type(),
                definition instanceof RowOperation rowOperation ? rowOperation : null,
                definition instanceof Aggregation aggregation ? aggregation : null,
                RunStatus.STARTING,
                resultTable,
                now,
                now);
    }

    public RunDefinition definition() {
        return rowOperation != null ? rowOperation : aggregation;
    }

}
