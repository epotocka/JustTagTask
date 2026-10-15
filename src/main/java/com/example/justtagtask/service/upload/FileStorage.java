package com.example.justtagtask.service.upload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.example.justtagtask.config.common.IngestionProperties;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

@Component
public class FileStorage {

    private final Path directory;

    @SneakyThrows
    FileStorage(IngestionProperties properties) {
        this.directory = Files.createDirectories(properties.storageDirectory().toAbsolutePath());
    }

    public Path pathFor(UUID fileId) {
        return directory.resolve(fileId + ".csv");
    }

    @SneakyThrows
    public void delete(Path path) {
        Files.deleteIfExists(path);
    }

}
