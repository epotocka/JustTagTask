package com.example.justtagtask.service.history;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.justtagtask.dto.common.PageView;
import com.example.justtagtask.dto.history.FileDetails;
import com.example.justtagtask.dto.history.FileSummary;
import com.example.justtagtask.dto.processing.ProcessingSummary;
import com.example.justtagtask.exception.common.NotFoundException;
import com.example.justtagtask.model.upload.UploadedFile;
import com.example.justtagtask.model.upload.enums.FileStatus;
import com.example.justtagtask.repository.upload.UploadedFileRepository;
import com.example.justtagtask.service.loading.CanonicalLoadService;
import com.example.justtagtask.service.processing.ProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileHistoryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final UploadedFileRepository uploadedFileRepository;
    private final CanonicalLoadService canonicalLoadService;
    private final ProcessingService processingService;

    public PageView<FileSummary> history(FileStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE), NEWEST_FIRST);
        Page<UploadedFile> result = status == null ? uploadedFileRepository.findAll(pageRequest) : uploadedFileRepository.findByStatus(status, pageRequest);
        Map<UUID, List<ProcessingSummary>> runsByFile = processingService.summariesByFile(result.map(UploadedFile::id).getContent());
        return PageView.of(result.map(file -> FileSummary.of(file, runsByFile.getOrDefault(file.id(), List.of()))));
    }

    public FileDetails details(UUID fileId) {
        UploadedFile file = uploadedFileRepository.findById(fileId).orElseThrow(() -> new NotFoundException("File " + fileId + " doesn't exist"));
        return FileDetails.of(file, canonicalLoadService.findStatus(file).orElse(null), processingService.summaries(fileId));
    }

}
