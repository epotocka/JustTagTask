package com.example.justtagtask.service.upload;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.example.justtagtask.model.canonical.enums.AdColumn;
import com.example.justtagtask.model.upload.enums.FileStatus;
import com.example.justtagtask.repository.upload.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HeaderCheck {

    private static final String BYTE_ORDER_MARK = "﻿";
    private static final int MAX_HEADER_LENGTH = 1024;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");
    private static final String UNPRINTABLE_REPLACEMENT = "?";

    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorage fileStorage;

    @Async
    public void verify(UUID fileId) {
        String expected = AdColumn.csvHeader();
        String actual = readHeader(fileStorage.pathFor(fileId));
        if (columnNames(expected).equals(columnNames(actual))) {
            uploadedFileRepository.transition(fileId, Set.of(FileStatus.UPLOADED), FileStatus.READY);
        } else {
            uploadedFileRepository.markFailed(fileId, "Expected the header '" + expected + "' but found '" + printable(actual) + "'");
        }
    }

    @SneakyThrows
    private static String readHeader(Path path) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
            char[] buffer = new char[MAX_HEADER_LENGTH];
            int read = Math.max(0, reader.read(buffer, 0, MAX_HEADER_LENGTH));
            return String.valueOf(buffer, 0, read).replace(BYTE_ORDER_MARK, "").lines().findFirst().orElse("").strip();
        }
    }

    private static String printable(String header) {
        return CONTROL_CHARACTERS.matcher(header).replaceAll(UNPRINTABLE_REPLACEMENT);
    }

    private static List<String> columnNames(String header) {
        return Arrays.stream(header.split(AdColumn.CSV_DELIMITER, -1)).map(String::strip).toList();
    }

}
