package com.example.justtagtask.controller;

import java.net.URI;
import java.util.UUID;

import com.example.justtagtask.dto.common.ExecutionView;
import com.example.justtagtask.service.loading.CanonicalLoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/files/{fileId}/load")
@RequiredArgsConstructor
class CanonicalLoadController {

    private final CanonicalLoadService canonicalLoadService;

    @PostMapping
    ResponseEntity<Void> start(@PathVariable UUID fileId) {
        canonicalLoadService.start(fileId);
        return ResponseEntity.accepted().location(statusLocation(fileId)).build();
    }

    @PostMapping("/stop")
    ResponseEntity<Void> stop(@PathVariable UUID fileId) {
        canonicalLoadService.stop(fileId);
        return ResponseEntity.accepted().location(statusLocation(fileId)).build();
    }

    @PostMapping("/restart")
    ResponseEntity<Void> restart(@PathVariable UUID fileId) {
        canonicalLoadService.restart(fileId);
        return ResponseEntity.accepted().location(statusLocation(fileId)).build();
    }

    @GetMapping
    ExecutionView status(@PathVariable UUID fileId) {
        return canonicalLoadService.status(fileId);
    }

    private static URI statusLocation(UUID fileId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/files/{fileId}/load")
                .buildAndExpand(fileId)
                .toUri();
    }

}
