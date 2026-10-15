package com.example.justtagtask.config.processing;

import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.example.justtagtask.config.common.IngestionProperties;
import com.example.justtagtask.model.processing.OperandRow;
import com.example.justtagtask.model.processing.ProcessingRun;
import com.example.justtagtask.model.processing.ResultTable;
import com.example.justtagtask.model.processing.RowResult;
import com.example.justtagtask.model.processing.enums.Operation;
import com.example.justtagtask.repository.processing.ProcessingRunRepository;
import com.example.justtagtask.service.processing.AggregationTasklet;
import com.example.justtagtask.service.processing.ProcessingJobListener;
import com.example.justtagtask.service.processing.ProcessingJobParameters;
import com.example.justtagtask.util.SqlUtils;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.SimplePropertySqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
class ProcessingJobConfiguration {

    private static final String RUN_ID_PARAMETER = "#{jobParameters['" + ProcessingJobParameters.RUN_ID + "']}";

    @Bean
    Job rowOperationJob(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcPagingItemReader<OperandRow> operandReader,
            ItemProcessor<OperandRow, RowResult> operationProcessor,
            JdbcBatchItemWriter<RowResult> rowResultWriter,
            ProcessingJobListener listener,
            IngestionProperties properties) {
        Step rowOperationStep = new StepBuilder(ProcessingJobParameters.ROW_OPERATION_STEP, jobRepository)
                .<OperandRow, RowResult>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(operandReader)
                .processor(operationProcessor)
                .writer(rowResultWriter)
                .build();
        return new JobBuilder(ProcessingJobParameters.ROW_OPERATION_JOB, jobRepository)
                .listener(listener)
                .start(rowOperationStep)
                .build();
    }

    @Bean
    Job aggregationJob(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            AggregationTasklet aggregationTasklet,
            ProcessingJobListener listener) {
        Step aggregationStep = new StepBuilder(ProcessingJobParameters.AGGREGATION_STEP, jobRepository)
                .tasklet(aggregationTasklet, transactionManager)
                .build();
        return new JobBuilder(ProcessingJobParameters.AGGREGATION_JOB, jobRepository)
                .listener(listener)
                .start(aggregationStep)
                .build();
    }

    @Bean
    @StepScope
    JdbcPagingItemReader<OperandRow> operandReader(
            @Value(RUN_ID_PARAMETER) String runId,
            ProcessingRunRepository processingRunRepository,
            DataSource dataSource,
            IngestionProperties properties) throws Exception {
        ProcessingRun run = processingRunRepository.findById(UUID.fromString(runId)).orElseThrow();
        return new JdbcPagingItemReaderBuilder<OperandRow>()
                .name("operandReader")
                .dataSource(dataSource)
                .selectClause(SqlUtils.operandColumns(run.rowOperation()))
                .fromClause(SqlUtils.AD_RECORD_TABLE)
                .whereClause(SqlUtils.FILE_ID_FILTER)
                .parameterValues(Map.of(SqlUtils.FILE_ID_PARAMETER, run.fileId()))
                .sortKeys(Map.of(SqlUtils.ROW_NUMBER_COLUMN, Order.ASCENDING))
                .pageSize(properties.chunkSize())
                .rowMapper((resultSet, rowNumber) -> new OperandRow(
                        resultSet.getLong(SqlUtils.ROW_NUMBER_COLUMN),
                        resultSet.getBigDecimal(SqlUtils.LEFT_OPERAND_COLUMN),
                        resultSet.getBigDecimal(SqlUtils.RIGHT_OPERAND_COLUMN)))
                .build();
    }

    @Bean
    @StepScope
    ItemProcessor<OperandRow, RowResult> operationProcessor(
            @Value(RUN_ID_PARAMETER) String runId,
            ProcessingRunRepository processingRunRepository) {
        Operation operation = processingRunRepository.findById(UUID.fromString(runId)).orElseThrow().rowOperation().operation();
        return row -> new RowResult(row.rowNumber(), operation.apply(row.left(), row.right()));
    }

    @Bean
    @StepScope
    JdbcBatchItemWriter<RowResult> rowResultWriter(@Value(RUN_ID_PARAMETER) String runId, DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<RowResult>()
                .dataSource(dataSource)
                .sql(SqlUtils.insertRowResult(new ResultTable(UUID.fromString(runId))))
                .assertUpdates(false)
                .itemSqlParameterSourceProvider(SimplePropertySqlParameterSource::new)
                .build();
    }

    @Bean
    @StepScope
    AggregationTasklet aggregationTasklet(
            @Value(RUN_ID_PARAMETER) String runId,
            ProcessingRunRepository processingRunRepository,
            JdbcTemplate jdbcTemplate) {
        return new AggregationTasklet(jdbcTemplate, processingRunRepository.findById(UUID.fromString(runId)).orElseThrow());
    }

}
