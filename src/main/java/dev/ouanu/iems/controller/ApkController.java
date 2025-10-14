package dev.ouanu.iems.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.ouanu.iems.dto.ApkSearchCriteria;
import dev.ouanu.iems.dto.ApkUpdateRequest;
import dev.ouanu.iems.entity.Apk;
import dev.ouanu.iems.service.ApkService;

@RestController
@RequestMapping("/api/apks")
public class ApkController {

    private final ApkService apkService;

    public ApkController(ApkService apkService) {
        this.apkService = apkService;
    }

    @PreAuthorize("hasAuthority('app:manage')")
    @PostMapping("/upload")
    public ResponseEntity<Apk> uploadApk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("organization") String organization,
            @RequestParam("group") String group) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }
        try {
            Apk savedApk = apkService.processAndSaveApk(file, organization, group);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedApk);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PreAuthorize("hasAuthority('app:manage')")
    @GetMapping
    public ResponseEntity<List<Apk>> listApks() {
        return ResponseEntity.ok(apkService.getAllApks());
    }

    @PreAuthorize("hasAuthority('app:read')")
    @GetMapping("/{id}")
    public ResponseEntity<Apk> getApkById(@PathVariable String id) {
        return apkService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('app:read')")
    @PostMapping("/query")
    public ResponseEntity<List<Apk>> queryApks(@RequestBody ApkSearchCriteria criteria) {
        return ResponseEntity.ok(apkService.queryApks(criteria));
    }

    @PreAuthorize("hasAuthority('app:manage')")
    @PutMapping("/{id}")
    public ResponseEntity<Apk> updateApk(@PathVariable String id, @RequestBody ApkUpdateRequest updateRequest) {
        try {
            return ResponseEntity.ok(apkService.updateApk(id, updateRequest));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("hasAuthority('app:manage')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApk(@PathVariable String id) {
        try {
            apkService.deleteApk(id);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}