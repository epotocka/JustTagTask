package com.example.justtagtask.model.processing;

import java.util.UUID;

public record ResultTable(UUID runId) {

    private static final String SCHEMA = "result";
    private static final String TABLE_PREFIX = "run_";

    public String name() {
        return SCHEMA + "." + TABLE_PREFIX + runId.toString().replace("-", "");
    }

}
