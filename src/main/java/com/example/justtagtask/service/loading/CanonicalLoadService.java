package com.example.justtagtask.service.loading;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.example.justtagtask.dto.common.ExecutionView;
import com.example.justtagtask.exception.common.ConflictException;
import com.example.justtagtask.exception.common.NotFoundException;
import com.example.justtagtask.model.upload.UploadedFile;
import com.example.justtagtask.model.upload.enums.FileStatus;
import com.example.justtagtask.repository.upload.UploadedFileRepository;
import com.example.justtagtask.service.common.BatchJobs;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Service;

// Not @Transactional: Spring Batch rejects JobOperator calls made inside a transaction and manages its own.
@Service
@RequiredArgsConstructor
public class CanonicalLoadService {

    private final UploadedFileRepository uploadedFileRepository;
    private final JobOperator jobOperator;
    private final Job loadFileJob;
    private final BatchJobs batchJobs;

    @SneakyThrows
    public void start(UUID fileId) {
        requireStatus(findFile(fileId), Set.of(FileStatus.READY), "only a READY file can be loaded");
        jobOperator.start(loadFileJob, CanonicalLoadJobParameters.forFile(fileId));
    }

    @SneakyThrows
    public void stop(UUID fileId) {
        jobOperator.stop(lastExecution(fileId));
    }

    @SneakyThrows
    public void restart(UUID fileId) {
        JobExecution execution = lastExecution(fileId);
        requireStatus(findFile(fileId), Set.of(FileStatus.STOPPED, FileStatus.FAILED), "only a STOPPED or FAILED load can be restarted");
        jobOperator.restart(execution);
    }

    public ExecutionView status(UUID fileId) {
        return findStatus(findFile(fileId))
                .orElseThrow(() -> new NotFoundException("The load of file " + fileId + " hasn't started"));
    }

    public Optional<ExecutionView> findStatus(UploadedFile file) {
        List<JobExecution> executions = executions(file.id());
        if (executions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ExecutionView.of(executions, CanonicalLoadJobParameters.STEP_NAME, file.rowCount()));
    }

    public long loadedRowCount(UUID fileId) {
        return executions(fileId).stream()
                .flatMap(execution -> execution.getStepExecutions().stream())
                .filter(step -> step.getStepName().equals(CanonicalLoadJobParameters.STEP_NAME))
                .mapToLong(StepExecution::getWriteCount)
                .sum();
    }

    private UploadedFile findFile(UUID fileId) {
        return uploadedFileRepository.findById(fileId).orElseThrow(() -> new NotFoundException("File " + fileId + " doesn't exist"));
    }

    private static void requireStatus(UploadedFile file, Set<FileStatus> allowed, String rule) {
        if (!allowed.contains(file.status())) {
            throw new ConflictException("File " + file.id() + " is " + file.status() + ", but " + rule);
        }
    }

    private JobExecution lastExecution(UUID fileId) {
        findFile(fileId);
        return batchJobs.lastExecution(CanonicalLoadJobParameters.JOB_NAME, CanonicalLoadJobParameters.forFile(fileId))
                .orElseThrow(() -> new ConflictException("The load of file " + fileId + " hasn't started"));
    }

    private List<JobExecution> executions(UUID fileId) {
        return batchJobs.executions(CanonicalLoadJobParameters.JOB_NAME, CanonicalLoadJobParameters.forFile(fileId));
    }

}
