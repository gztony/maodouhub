package com.zhengmeng.hub.controller;

import com.zhengmeng.hub.config.HubProperties;
import com.zhengmeng.hub.model.HubFile;
import com.zhengmeng.hub.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传下载控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileRepository fileRepository;
    private final HubProperties properties;

    /** 上传文件（手机端） */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file, Authentication auth) throws IOException {
        String userId = auth.getName();
        String fileId = "file-" + UUID.randomUUID().toString().substring(0, 8);

        // 存储路径：{storageDir}/{userId}/{yyyy-MM}/{fileId}_{fileName}
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Path dir = Path.of(properties.getFile().getStorageDir(), userId, month);
        Files.createDirectories(dir);

        String safeFileName = file.getOriginalFilename() != null
            ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_")
            : "upload";
        Path filePath = dir.resolve(fileId + "_" + safeFileName);
        file.transferTo(filePath.toFile());

        // 入库
        HubFile hubFile = new HubFile();
        hubFile.setFileId(fileId);
        hubFile.setUserId(userId);
        hubFile.setFileName(file.getOriginalFilename());
        hubFile.setFileSize(file.getSize());
        hubFile.setContentType(file.getContentType());
        hubFile.setStoragePath(filePath.toString());
        hubFile.setDirection("upload");
        fileRepository.save(hubFile);

        log.info("文件上传: userId={}, fileId={}, name={}, size={}", userId, fileId, file.getOriginalFilename(), file.getSize());

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "fileId", fileId,
            "fileName", hubFile.getFileName(),
            "fileSize", hubFile.getFileSize()
        ));
    }

    /** 下载文件 */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<?> download(@PathVariable String fileId) {
        HubFile hubFile = fileRepository.findById(fileId).orElse(null);
        if (hubFile == null) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(hubFile.getStoragePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + hubFile.getFileName() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(hubFile.getFileSize())
            .body(new FileSystemResource(file));
    }

    /** 文件元信息 */
    @GetMapping("/{fileId}/info")
    public ResponseEntity<?> info(@PathVariable String fileId) {
        HubFile hubFile = fileRepository.findById(fileId).orElse(null);
        if (hubFile == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "fileId", hubFile.getFileId(),
            "fileName", hubFile.getFileName(),
            "fileSize", hubFile.getFileSize(),
            "contentType", hubFile.getContentType() != null ? hubFile.getContentType() : "",
            "direction", hubFile.getDirection(),
            "createdAt", hubFile.getCreatedAt().toString()
        ));
    }
}
