package com.example.justtagtask.service.upload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.example.justtagtask.config.common.IngestionProperties;
import com.example.justtagtask.exception.upload.FileTooLargeException;
import com.example.justtagtask.exception.upload.InvalidUploadException;
import com.example.justtagtask.model.upload.UploadedFile;
import com.example.justtagtask.repository.upload.UploadedFileRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UploadService {

    private static final String FILE_PART = "file";
    private static final String UNNAMED_FILE = "unnamed.csv";
    private static final int BUFFER_SIZE = 64 * 1024;

    private final UploadedFileRepository uploadedFileRepository;
    private final JdbcAggregateTemplate jdbcAggregateTemplate;
    private final FileStorage fileStorage;
    private final HeaderCheck headerCheck;
    private final IngestionProperties ingestionProperties;

    // Not @Transactional: an upload can take minutes, and its progress must stay visible to the status endpoint meanwhile.
    public UUID upload(HttpServletRequest request) {
        FileItemInput part = findFilePart(request);
        UUID fileId = UUID.randomUUID();
        Path target = fileStorage.pathFor(fileId);
        jdbcAggregateTemplate.insert(UploadedFile.uploading(fileId, filenameOf(part), target));
        receive(fileId, part, target);
        headerCheck.verify(fileId);
        return fileId;
    }

    @SneakyThrows
    private FileItemInput findFilePart(HttpServletRequest request) {
        if (!JakartaServletFileUpload.isMultipartContent(request)) {
            throw new InvalidUploadException("Expected a multipart/form-data request");
        }
        FileItemInputIterator parts = new JakartaServletFileUpload<DiskFileItem, DiskFileItemFactory>().getItemIterator(request);
        while (parts.hasNext()) {
            FileItemInput part = parts.next();
            if (!part.isFormField() && FILE_PART.equals(part.getFieldName())) {
                return part;
            }
        }
        throw new InvalidUploadException("The request has no '" + FILE_PART + "' part");
    }

    private static String filenameOf(FileItemInput part) {
        String name = part.getName();
        return name == null || name.isBlank() ? UNNAMED_FILE : Path.of(name).getFileName().toString();
    }

    @SneakyThrows
    private void receive(UUID fileId, FileItemInput part, Path target) {
        try (OutputStream out = Files.newOutputStream(target)) {
            UploadProgress progress = copy(fileId, part.getInputStream(), out);
            uploadedFileRepository.finishUpload(fileId, progress.bytes(), progress.rows());
        } catch (FileTooLargeException e) {
            discard(fileId, target, e.getBody().getDetail());
            throw e;
        } catch (IOException e) {
            discard(fileId, target, "The upload was interrupted: " + e.getMessage());
            throw e;
        }
    }

    private UploadProgress copy(UUID fileId, InputStream in, OutputStream out) throws IOException {
        UploadProgress progress = new UploadProgress(ingestionProperties.maxFileSize().toBytes());
        Duration saveInterval = ingestionProperties.progressSaveInterval();
        byte[] buffer = new byte[BUFFER_SIZE];
        Instant nextProgressSave = Instant.now().plus(saveInterval);
        int read;
        while ((read = in.read(buffer)) != -1) {
            progress.record(buffer, read);
            out.write(buffer, 0, read);
            if (!Instant.now().isBefore(nextProgressSave)) {
                uploadedFileRepository.updateProgress(fileId, progress.bytes(), progress.rows());
                nextProgressSave = Instant.now().plus(saveInterval);
            }
        }
        return progress;
    }

    private void discard(UUID fileId, Path target, String reason) {
        uploadedFileRepository.markFailed(fileId, reason);
        fileStorage.delete(target);
    }

}
