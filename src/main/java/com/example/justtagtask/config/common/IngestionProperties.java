package com.example.justtagtask.config.common;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("ingestion")
public record IngestionProperties(
        Path storageDirectory,
        DataSize maxFileSize,
        Duration progressSaveInterval,
        int chunkSize,
        int skipLimit) {
}
