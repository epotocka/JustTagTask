package com.example.justtagtask.model.processing;

import com.example.justtagtask.model.canonical.enums.AdColumn;
import com.example.justtagtask.model.processing.enums.AggregateFunction;
import com.example.justtagtask.model.processing.enums.RunType;

public record Aggregation(AdColumn groupBy, AdColumn valueColumn, AggregateFunction function) implements RunDefinition {

    @Override
    public RunType type() {
        return RunType.AGGREGATION;
    }

}
