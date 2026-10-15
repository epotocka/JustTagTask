package com.example.justtagtask.repository.upload;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.example.justtagtask.model.upload.UploadedFile;
import com.example.justtagtask.model.upload.enums.FileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface UploadedFileRepository
        extends ListCrudRepository<UploadedFile, UUID>, PagingAndSortingRepository<UploadedFile, UUID> {

    Page<UploadedFile> findByStatus(FileStatus status, Pageable pageable);

    List<UploadedFile> findAllByStatus(FileStatus status);

    @Modifying
    @Query("""
            UPDATE uploaded_file
            SET bytes_received = :bytesReceived, row_count = :rowCount, updated_at = now()
            WHERE id = :id
            """)
    void updateProgress(UUID id, long bytesReceived, long rowCount);

    @Modifying
    @Query("""
            UPDATE uploaded_file
            SET status = 'UPLOADED', bytes_received = :bytesReceived, row_count = :rowCount, updated_at = now()
            WHERE id = :id
            """)
    void finishUpload(UUID id, long bytesReceived, long rowCount);

    @Modifying
    @Query("""
            UPDATE uploaded_file
            SET status = :to, failure_reason = NULL, updated_at = now()
            WHERE id = :id AND status IN (:from)
            """)
    boolean transition(UUID id, Collection<FileStatus> from, FileStatus to);

    @Modifying
    @Query("""
            UPDATE uploaded_file
            SET status = :status, updated_at = now()
            WHERE id = :id
            """)
    void updateStatus(UUID id, FileStatus status);

    @Modifying
    @Query("""
            UPDATE uploaded_file
            SET status = 'FAILED', failure_reason = :reason, updated_at = now()
            WHERE id = :id
            """)
    void markFailed(UUID id, String reason);

}
