package com.example.justtagtask.service.processing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.example.justtagtask.model.canonical.enums.AdColumn;
import com.example.justtagtask.model.processing.Aggregation;
import com.example.justtagtask.model.processing.ProcessingRun;
import com.example.justtagtask.model.processing.enums.AggregateFunction;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobInterruptedException;
import org.springframework.jdbc.core.JdbcTemplate;

class AggregationTaskletTest {

    private final ProcessingRun run = ProcessingRun.starting(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new Aggregation(AdColumn.CAMPAIGN_ID, AdColumn.CLICKS, AggregateFunction.SUM),
            "result.run");

    @Test
    void refusesToStartOnceStopped() {
        AggregationTasklet tasklet = new AggregationTasklet(new JdbcTemplate(), run);

        tasklet.stop();

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(JobInterruptedException.class)
                .hasMessage("The aggregation was stopped before it started");
    }

}
