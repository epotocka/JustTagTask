package com.example.justtagtask.service.processing;

import java.util.UUID;

import com.example.justtagtask.model.processing.ProcessingRun;
import com.example.justtagtask.model.processing.ResultTable;
import com.example.justtagtask.model.processing.RunDefinition;
import com.example.justtagtask.util.SqlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProcessingRunCreator {

    private final JdbcAggregateTemplate jdbcAggregateTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public ProcessingRun create(UUID fileId, RunDefinition definition) {
        UUID runId = UUID.randomUUID();
        ResultTable resultTable = new ResultTable(runId);
        ProcessingRun run = ProcessingRun.starting(runId, fileId, definition, resultTable.name());
        jdbcAggregateTemplate.insert(run);
        jdbcTemplate.execute(SqlUtils.createResultTable(resultTable, definition));
        return run;
    }

}
