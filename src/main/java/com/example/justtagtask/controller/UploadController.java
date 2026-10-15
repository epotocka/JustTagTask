package com.example.justtagtask.controller;

import java.net.URI;
import java.util.UUID;

import com.example.justtagtask.dto.upload.UploadResponse;
import com.example.justtagtask.model.upload.enums.FileStatus;
import com.example.justtagtask.service.upload.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
class UploadController {

    private final UploadService uploadService;

    @PostMapping("/api/files")
    ResponseEntity<UploadResponse> upload(HttpServletRequest request) {
        UUID fileId = uploadService.upload(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(fileId).toUri();
        return ResponseEntity.accepted().location(location).body(new UploadResponse(fileId, FileStatus.UPLOADED));
    }

}
