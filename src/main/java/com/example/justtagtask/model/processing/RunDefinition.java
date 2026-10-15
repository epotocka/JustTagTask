package com.example.justtagtask.model.processing;

import com.example.justtagtask.model.processing.enums.RunType;

public sealed interface RunDefinition permits RowOperation, Aggregation {

    RunType type();

}
