package com.example.justtagtask.service.processing;

import java.sql.PreparedStatement;
import java.util.UUID;

import com.example.justtagtask.model.processing.ProcessingRun;
import com.example.justtagtask.model.processing.ResultTable;
import com.example.justtagtask.util.SqlUtils;
import lombok.SneakyThrows;
import org.springframework.batch.core.job.JobInterruptedException;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.StoppableTasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

public class AggregationTasklet implements StoppableTasklet {

    private final JdbcTemplate jdbcTemplate;
    private final String statement;
    private final UUID fileId;
    private volatile boolean stopped;
    private volatile PreparedStatement runningStatement;

    public AggregationTasklet(JdbcTemplate jdbcTemplate, ProcessingRun run) {
        this.jdbcTemplate = jdbcTemplate;
        this.statement = SqlUtils.insertAggregationResults(new ResultTable(run.id()), run.aggregation());
        this.fileId = run.fileId();
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (stopped) {
            throw new JobInterruptedException("The aggregation was stopped before it started");
        }
        try {
            Integer groups = jdbcTemplate.execute(statement, (PreparedStatementCallback<Integer>) prepared -> {
                runningStatement = prepared;
                prepared.setObject(1, fileId);
                return prepared.executeUpdate();
            });
            contribution.incrementWriteCount(groups);
        } catch (DataAccessException e) {
            if (stopped) {
                throw new JobInterruptedException("The aggregation was stopped");
            }
            throw e;
        } finally {
            runningStatement = null;
        }
        return RepeatStatus.FINISHED;
    }

    @Override
    @SneakyThrows
    public void stop() {
        stopped = true;
        PreparedStatement running = runningStatement;
        if (running != null) {
            running.cancel();
        }
    }

}
