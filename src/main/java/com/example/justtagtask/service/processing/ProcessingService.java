package com.example.justtagtask.service.processing;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.justtagtask.dto.common.ExecutionView;
import com.example.justtagtask.dto.processing.ProcessingRequest;
import com.example.justtagtask.dto.processing.ProcessingSummary;
import com.example.justtagtask.dto.processing.ProcessingView;
import com.example.justtagtask.exception.common.ConflictException;
import com.example.justtagtask.exception.common.NotFoundException;
import com.example.justtagtask.model.processing.ProcessingRun;
import com.example.justtagtask.model.processing.enums.RunStatus;
import com.example.justtagtask.model.processing.enums.RunType;
import com.example.justtagtask.model.upload.UploadedFile;
import com.example.justtagtask.model.upload.enums.FileStatus;
import com.example.justtagtask.repository.processing.ProcessingRunRepository;
import com.example.justtagtask.repository.upload.UploadedFileRepository;
import com.example.justtagtask.service.common.BatchJobs;
import com.example.justtagtask.service.loading.CanonicalLoadService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;

// Not @Transactional: Spring Batch rejects JobOperator calls made inside a transaction, so ProcessingRunCreator commits the run first.
@Service
@RequiredArgsConstructor
public class ProcessingService {

    private static final Set<RunStatus> ACTIVE = Set.of(RunStatus.STARTING, RunStatus.STARTED);
    private static final Set<RunStatus> RESTARTABLE = Set.of(RunStatus.STOPPED, RunStatus.FAILED);

    private final UploadedFileRepository uploadedFileRepository;
    private final ProcessingRunRepository processingRunRepository;
    private final ProcessingRunCreator processingRunCreator;
    private final JobOperator jobOperator;
    private final Job rowOperationJob;
    private final Job aggregationJob;
    private final BatchJobs batchJobs;
    private final CanonicalLoadService canonicalLoadService;

    @SneakyThrows
    public ProcessingSummary start(UUID fileId, ProcessingRequest request) {
        UploadedFile file = uploadedFileRepository.findById(fileId).orElseThrow(() -> new NotFoundException("File " + fileId + " doesn't exist"));
        if (file.status() != FileStatus.LOADED) {
            throw new ConflictException("File " + fileId + " is " + file.status() + ", but only a LOADED file can be processed");
        }
        requireNoActiveRun(fileId);
        ProcessingRun run = processingRunCreator.create(fileId, request.toDefinition());
        jobOperator.start(jobFor(run.type()), ProcessingJobParameters.forRun(run.id()));
        return ProcessingSummary.of(run);
    }

    @SneakyThrows
    public void stop(UUID fileId, UUID runId) {
        jobOperator.stop(lastExecution(findRun(fileId, runId)));
    }

    @SneakyThrows
    public void restart(UUID fileId, UUID runId) {
        ProcessingRun run = findRun(fileId, runId);
        if (!RESTARTABLE.contains(run.status())) {
            throw new ConflictException("Run " + runId + " is " + run.status() + ", but only a STOPPED or FAILED run can be restarted");
        }
        requireNoActiveRun(fileId);
        jobOperator.restart(lastExecution(run));
    }

    public ProcessingView view(UUID fileId, UUID runId) {
        ProcessingRun run = findRun(fileId, runId);
        List<JobExecution> executions = executions(run);
        ExecutionView execution = executions.isEmpty() ? null : ExecutionView.of(
                executions,
                ProcessingJobParameters.mainStep(run.type()),
                run.type() == RunType.OPERATION ? canonicalLoadService.loadedRowCount(fileId) : null);
        return ProcessingView.of(run, execution);
    }

    public List<ProcessingSummary> summaries(UUID fileId) {
        return processingRunRepository.findByFileIdOrderByCreatedAtDesc(fileId).stream().map(ProcessingSummary::of).toList();
    }

    public Map<UUID, List<ProcessingSummary>> summariesByFile(Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return processingRunRepository.findByFileIdInOrderByCreatedAtDesc(fileIds).stream()
                .collect(Collectors.groupingBy(ProcessingRun::fileId, Collectors.mapping(ProcessingSummary::of, Collectors.toList())));
    }

    private void requireNoActiveRun(UUID fileId) {
        if (processingRunRepository.existsByFileIdAndStatusIn(fileId, ACTIVE)) {
            throw new ConflictException("File " + fileId + " already has a run in progress");
        }
    }

    private ProcessingRun findRun(UUID fileId, UUID runId) {
        return processingRunRepository.findById(runId)
                .filter(run -> run.fileId().equals(fileId))
                .orElseThrow(() -> new NotFoundException("Run " + runId + " doesn't exist for file " + fileId));
    }

    private JobExecution lastExecution(ProcessingRun run) {
        List<JobExecution> executions = executions(run);
        if (executions.isEmpty()) {
            throw new ConflictException("Run " + run.id() + " hasn't started");
        }
        return executions.getLast();
    }

    private List<JobExecution> executions(ProcessingRun run) {
        return batchJobs.executions(ProcessingJobParameters.jobName(run.type()), ProcessingJobParameters.forRun(run.id()));
    }

    private Job jobFor(RunType type) {
        return switch (type) {
            case OPERATION -> rowOperationJob;
            case AGGREGATION -> aggregationJob;
        };
    }

}
