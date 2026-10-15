package com.example.justtagtask.service.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import com.example.justtagtask.exception.upload.FileTooLargeException;
import org.junit.jupiter.api.Test;

class UploadProgressTest {

    @Test
    void countsDataRowsWithoutTheHeader() {
        UploadProgress progress = recorded("header\nrow1\nrow2\n");

        assertThat(progress.rows()).isEqualTo(2);
        assertThat(progress.bytes()).isEqualTo(17);
    }

    @Test
    void countsALastRowWithoutALineBreak() {
        assertThat(recorded("header\nrow1\nrow2").rows()).isEqualTo(2);
    }

    @Test
    void countsRowsSplitAcrossBuffers() {
        UploadProgress progress = new UploadProgress(1_000);
        record(progress, "header\nro");
        record(progress, "w1\nrow2\n");

        assertThat(progress.rows()).isEqualTo(2);
    }

    @Test
    void reportsNoRowsForAHeaderOnly() {
        assertThat(recorded("header\n").rows()).isZero();
    }

    @Test
    void reportsNoRowsForAnEmptyFile() {
        assertThat(new UploadProgress(1_000).rows()).isZero();
    }

    @Test
    void rejectsBytesOverTheLimit() {
        UploadProgress progress = new UploadProgress(5);

        assertThatThrownBy(() -> record(progress, "123456")).isInstanceOf(FileTooLargeException.class);
    }

    private static UploadProgress recorded(String content) {
        UploadProgress progress = new UploadProgress(1_000);
        record(progress, content);
        return progress;
    }

    private static void record(UploadProgress progress, String chunk) {
        byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
        progress.record(bytes, bytes.length);
    }

}
