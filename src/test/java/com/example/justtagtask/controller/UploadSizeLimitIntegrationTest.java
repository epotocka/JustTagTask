package com.example.justtagtask.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import com.example.justtagtask.IntegrationTest;
import com.example.justtagtask.TestCsv;
import com.example.justtagtask.service.upload.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UploadSizeLimitIntegrationTest extends IntegrationTest {

    @Autowired
    private FileStorage fileStorage;

    @Test
    void rejectsAFileOverTheLimitAndDeletesThePartialFile() throws IOException {
        Path oversized = TestCsv.generated(5_000);
        assertThat(Files.size(oversized)).isGreaterThan(MAX_FILE_SIZE_BYTES);

        postFile(oversized)
                .expectStatus().isEqualTo(413)
                .expectBody().jsonPath("$.detail")
                .isEqualTo("The file is larger than the limit of " + MAX_FILE_SIZE_BYTES + " bytes");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, status FROM uploaded_file WHERE original_filename = ?", oversized.getFileName().toString());
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(fileStorage.pathFor((UUID) row.get("id"))).doesNotExist();
    }

}
