package com.example.justtagtask.model.processing;

import com.example.justtagtask.model.canonical.enums.AdColumn;
import com.example.justtagtask.model.processing.enums.Operation;
import com.example.justtagtask.model.processing.enums.RunType;

public record RowOperation(AdColumn leftColumn, AdColumn rightColumn, Operation operation) implements RunDefinition {

    @Override
    public RunType type() {
        return RunType.OPERATION;
    }

}
