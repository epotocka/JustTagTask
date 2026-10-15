package com.example.justtagtask.service.upload;

import java.nio.file.Path;

import com.example.justtagtask.model.upload.UploadedFile;
import com.example.justtagtask.model.upload.enums.FileStatus;
import com.example.justtagtask.repository.upload.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterruptedUploadRecovery implements ApplicationRunner {

    private static final String REASON = "The server stopped during the upload";

    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorage fileStorage;

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }

    // Not @Transactional: each update is a single statement, and a deleted file can't be rolled back.
    public void recover() {
        for (UploadedFile file : uploadedFileRepository.findAllByStatus(FileStatus.UPLOADING)) {
            uploadedFileRepository.markFailed(file.id(), REASON);
            fileStorage.delete(Path.of(file.storagePath()));
        }
    }

}
