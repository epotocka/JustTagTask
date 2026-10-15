package com.example.justtagtask.controller;

import java.net.URI;
import java.util.UUID;

import com.example.justtagtask.dto.processing.ProcessingRequest;
import com.example.justtagtask.dto.processing.ProcessingSummary;
import com.example.justtagtask.dto.processing.ProcessingView;
import com.example.justtagtask.service.processing.ProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/files/{fileId}/process")
@RequiredArgsConstructor
class ProcessingController {

    private final ProcessingService processingService;

    @PostMapping
    ResponseEntity<ProcessingSummary> start(@PathVariable UUID fileId, @RequestBody ProcessingRequest request) {
        ProcessingSummary run = processingService.start(fileId, request);
        return ResponseEntity.accepted().location(runLocation(fileId, run.runId())).body(run);
    }

    @PostMapping("/{runId}/stop")
    ResponseEntity<Void> stop(@PathVariable UUID fileId, @PathVariable UUID runId) {
        processingService.stop(fileId, runId);
        return ResponseEntity.accepted().location(runLocation(fileId, runId)).build();
    }

    @PostMapping("/{runId}/restart")
    ResponseEntity<Void> restart(@PathVariable UUID fileId, @PathVariable UUID runId) {
        processingService.restart(fileId, runId);
        return ResponseEntity.accepted().location(runLocation(fileId, runId)).build();
    }

    @GetMapping("/{runId}")
    ProcessingView view(@PathVariable UUID fileId, @PathVariable UUID runId) {
        return processingService.view(fileId, runId);
    }

    private static URI runLocation(UUID fileId, UUID runId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/files/{fileId}/process/{runId}")
                .buildAndExpand(fileId, runId)
                .toUri();
    }

}
