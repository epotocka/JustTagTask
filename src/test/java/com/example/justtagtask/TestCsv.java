package com.example.justtagtask;

import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.example.justtagtask.model.canonical.enums.AdColumn;
import lombok.SneakyThrows;

public final class TestCsv {

    public static final Path SAMPLE = Path.of("src/main/resources/sample_data/sample_ads_data.csv");

    private static final Path DIRECTORY = Path.of("target/test-csv");

    private TestCsv() {
    }

    @SneakyThrows
    public static Path generated(int rows) {
        Random random = new Random(rows);
        Path path = newFile();
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(AdColumn.csvHeader());
            writer.newLine();
            for (int row = 0; row < rows; row++) {
                writer.write(randomLine(random));
                writer.newLine();
            }
        }
        return path;
    }

    @SneakyThrows
    public static Path withLines(String header, List<String> lines) {
        return Files.writeString(newFile(), header + "\n" + String.join("\n", lines) + "\n");
    }

    @SneakyThrows
    public static List<String> dataLines(Path csv) {
        List<String> lines = Files.readAllLines(csv);
        return lines.subList(1, lines.size());
    }

    private static String randomLine(Random random) {
        long impressions = random.nextInt(5_000);
        long clicks = impressions == 0 ? 0 : random.nextInt((int) impressions);
        return String.join(AdColumn.CSV_DELIMITER,
                String.valueOf(1_000 + random.nextInt(9_000)),
                String.valueOf(10_000 + random.nextInt(50)),
                String.valueOf(500_000 + random.nextInt(50_000)),
                new UUID(random.nextLong(), random.nextLong()).toString(),
                String.valueOf(impressions),
                String.valueOf(clicks),
                BigDecimal.valueOf(random.nextInt(100_000), 4).toPlainString(),
                BigDecimal.valueOf(random.nextInt(10_000), 4).toPlainString());
    }

    @SneakyThrows
    private static Path newFile() {
        Files.createDirectories(DIRECTORY);
        return DIRECTORY.resolve(UUID.randomUUID() + ".csv");
    }

}
