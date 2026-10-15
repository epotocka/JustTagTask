package com.example.justtagtask.config.loading;

import java.util.UUID;

import javax.sql.DataSource;

import com.example.justtagtask.config.common.IngestionProperties;
import com.example.justtagtask.model.canonical.AdRecord;
import com.example.justtagtask.service.loading.AdRecordLineMapper;
import com.example.justtagtask.service.loading.CanonicalLoadJobListener;
import com.example.justtagtask.service.loading.CanonicalLoadJobParameters;
import com.example.justtagtask.service.loading.MalformedLineLogger;
import com.example.justtagtask.service.upload.FileStorage;
import com.example.justtagtask.util.SqlUtils;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.namedparam.SimplePropertySqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
class CanonicalLoadJobConfiguration {

    @Bean
    Job loadFileJob(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<AdRecord> adRecordReader,
            JdbcBatchItemWriter<AdRecord> adRecordWriter,
            CanonicalLoadJobListener listener,
            MalformedLineLogger malformedLineLogger,
            IngestionProperties properties) {
        Step loadRecordsStep = new StepBuilder(CanonicalLoadJobParameters.STEP_NAME, jobRepository)
                .<AdRecord, AdRecord>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(adRecordReader)
                .writer(adRecordWriter)
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skipLimit(properties.skipLimit())
                .skipListener(malformedLineLogger)
                .build();
        return new JobBuilder(CanonicalLoadJobParameters.JOB_NAME, jobRepository)
                .listener(listener)
                .start(loadRecordsStep)
                .build();
    }

    @Bean
    @StepScope
    FlatFileItemReader<AdRecord> adRecordReader(
            @Value("#{jobParameters['" + CanonicalLoadJobParameters.FILE_ID + "']}") String fileId,
            FileStorage storage) {
        UUID id = UUID.fromString(fileId);
        return new FlatFileItemReaderBuilder<AdRecord>()
                .name("adRecordReader")
                .resource(new FileSystemResource(storage.pathFor(id)))
                .linesToSkip(1)
                .comments()
                .lineMapper(new AdRecordLineMapper(id))
                .build();
    }

    @Bean
    JdbcBatchItemWriter<AdRecord> adRecordWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<AdRecord>()
                .dataSource(dataSource)
                .sql(SqlUtils.INSERT_AD_RECORD)
                .assertUpdates(false)
                .itemSqlParameterSourceProvider(SimplePropertySqlParameterSource::new)
                .build();
    }

}
