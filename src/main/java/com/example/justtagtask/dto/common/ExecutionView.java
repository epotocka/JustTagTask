package com.example.justtagtask.dto.common;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

public record ExecutionView(
        BatchStatus status,
        long readCount,
        long writeCount,
        long skipCount,
        Double progressPercent,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String exitMessage) {

    public static ExecutionView of(List<JobExecution> executions, String countedStep, Long totalRows) {
        JobExecution first = executions.getFirst();
        JobExecution last = executions.getLast();
        List<StepExecution> steps = executions.stream()
                .flatMap(execution -> execution.getStepExecutions().stream())
                .filter(step -> step.getStepName().equals(countedStep))
                .toList();
        long readCount = steps.stream().mapToLong(StepExecution::getReadCount).sum();
        long readSkipCount = steps.stream().mapToLong(StepExecution::getReadSkipCount).sum();
        long writeCount = steps.stream().mapToLong(StepExecution::getWriteCount).sum();
        long skipCount = steps.stream().mapToLong(StepExecution::getSkipCount).sum();
        String exitMessage = last.getExitStatus().getExitDescription();
        return new ExecutionView(
                last.getStatus(),
                readCount,
                writeCount,
                skipCount,
                progress(last.getStatus(), readCount + readSkipCount, totalRows),
                first.getStartTime(),
                last.getEndTime(),
                exitMessage.isEmpty() ? null : exitMessage);
    }

    private static Double progress(BatchStatus status, long processedRows, Long totalRows) {
        if (status == BatchStatus.COMPLETED) {
            return 100.0;
        }
        if (totalRows == null || totalRows == 0) {
            return null;
        }
        double percent = Math.min(100.0, processedRows * 100.0 / totalRows);
        return Math.round(percent * 10) / 10.0;
    }

}
