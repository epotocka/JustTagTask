package com.example.justtagtask.controller;

import java.util.UUID;

import com.example.justtagtask.dto.common.PageView;
import com.example.justtagtask.dto.history.FileDetails;
import com.example.justtagtask.dto.history.FileSummary;
import com.example.justtagtask.model.upload.enums.FileStatus;
import com.example.justtagtask.service.history.FileHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
class FileHistoryController {

    private final FileHistoryService fileHistoryService;

    @GetMapping
    PageView<FileSummary> history(
            @RequestParam(required = false) FileStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return fileHistoryService.history(status, page, size);
    }

    @GetMapping("/{fileId}")
    FileDetails details(@PathVariable UUID fileId) {
        return fileHistoryService.details(fileId);
    }

}
