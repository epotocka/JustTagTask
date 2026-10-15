package com.example.justtagtask.repository.processing;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.example.justtagtask.model.processing.ProcessingRun;
import com.example.justtagtask.model.processing.enums.RunStatus;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ProcessingRunRepository extends ListCrudRepository<ProcessingRun, UUID> {

    List<ProcessingRun> findByFileIdOrderByCreatedAtDesc(UUID fileId);

    List<ProcessingRun> findByFileIdInOrderByCreatedAtDesc(Collection<UUID> fileIds);

    boolean existsByFileIdAndStatusIn(UUID fileId, Collection<RunStatus> statuses);

    @Modifying
    @Query("""
            UPDATE processing_run
            SET status = :status, updated_at = now()
            WHERE id = :id
            """)
    void updateStatus(UUID id, RunStatus status);

}
