package dev.ouanu.iems.vo;

import java.io.Serializable;
import java.util.Map;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import dev.ouanu.iems.entity.Apk;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApkVO implements Serializable {
    private String appName;
    private String packageName;
    private long versionCode;
    private String versionName;
    private String imageDownloadUrl;
    private String fileDownloadUrl;

    public static ApkVO fromEntity(Apk apk, String baseUrl) {
        if (apk == null) {
            return null;
        }
        ApkVO vo = new ApkVO();
        vo.setPackageName(apk.getPackageName());
        vo.setVersionName(apk.getVersionName());
        vo.setVersionCode(apk.getVersionCode());

        Map<String, String> labels = apk.getLabels();
        if (labels != null && !labels.isEmpty()) {
            String defaultLabel = labels.get("default");
            if (!StringUtils.hasText(defaultLabel)) {
                defaultLabel = labels.values().stream().findFirst().orElse(null);
            }
            vo.setAppName(defaultLabel);
        }

        if (StringUtils.hasText(apk.getIconPath())) {
            String iconRelative = ensureStoragePrefix(apk.getIconPath());
            vo.setImageDownloadUrl(buildAbsoluteUrl(baseUrl, iconRelative));
        }

        if (StringUtils.hasText(apk.getFilePath())) {
            String fileRelative = ensureStoragePrefix(apk.getFilePath());
            vo.setFileDownloadUrl(buildAbsoluteUrl(baseUrl, fileRelative));
        }
        return vo;
    }

    private static String ensureStoragePrefix(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        String normalized = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        if (normalized.startsWith("storage/")) {
            return "/" + normalized;
        }
        return "/storage/" + normalized;
    }

    private static String buildAbsoluteUrl(String baseUrl, String relativePath) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(relativePath)) {
            return null;
        }
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path(relativePath)
                .build()
                .toUriString();
    }
}
