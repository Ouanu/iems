package dev.ouanu.iems.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.ouanu.iems.dto.ApkSearchCriteria;
import dev.ouanu.iems.dto.ApkUpdateRequest;
import dev.ouanu.iems.dto.BatchUpdateApksRequest;
import dev.ouanu.iems.entity.Apk;
import dev.ouanu.iems.service.ApkService;
import dev.ouanu.iems.vo.ApkVO;

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
    @GetMapping("/summary")
    public ResponseEntity<List<ApkVO>> listApkSummaries(@RequestParam  int offset, @RequestParam int limit) {
        System.out.println("client ----------------" + offset + "  " + limit);
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        List<ApkVO> summaries = apkService.queryApks(new ApkSearchCriteria(null, null, null, null, null, true, offset, limit)).stream()
                .map(apk -> ApkVO.fromEntity(apk, baseUrl))
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @PreAuthorize("hasAuthority('app:read')")
    @PostMapping("/query")
    public ResponseEntity<List<Apk>> queryApks(@RequestBody ApkSearchCriteria criteria) {
        return ResponseEntity.ok(apkService.queryApks(criteria));
    }

    @PreAuthorize("hasAuthority('app:manage')")
    @PutMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> batchUpdateApks(@Valid @RequestBody BatchUpdateApksRequest request) {
        request.normalize();
        if (!request.hasUpdates()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("请至少提供一个需要更新的字段");
        }
        try {
            apkService.adminBatchUpdateApks(request.getIds(), request.getOrganization(), request.getGroup());
            return ResponseEntity.ok("批量更新成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
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

    @PreAuthorize("hasAuthority('app:manage')")
    @DeleteMapping
    public ResponseEntity<String> deleteApks(@RequestParam("ids") String idsParam) {
        if (!StringUtils.hasText(idsParam)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("ids参数不能为空");
        }
        List<String> ids = Arrays.stream(idsParam.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (ids.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("未提供有效的ID");
        }
        try {
            apkService.deleteApks(ids);
            return ResponseEntity.ok("批量删除成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("删除APK文件失败");
        }
    }
}