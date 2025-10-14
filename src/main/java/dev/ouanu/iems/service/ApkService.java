package dev.ouanu.iems.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import dev.ouanu.iems.dto.ApkSearchCriteria;
import dev.ouanu.iems.dto.ApkUpdateRequest;
import dev.ouanu.iems.entity.Apk;
import dev.ouanu.iems.repository.ApkRepository;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.Icon;

@Service
public class ApkService {
    private final ApkRepository apkRepository;
    private final Path apkStorageLocation;
    private final Path iconStorageLocation;
    private final MongoTemplate mongoTemplate;

    public ApkService(ApkRepository apkRepository,
                      MongoTemplate mongoTemplate,
                      @Value("${file.storage.apks-dir:./storage/apks}") String apksDir,
                      @Value("${file.storage.icons-dir:./storage/icons}") String iconsDir) {
        this.apkRepository = apkRepository;
        this.mongoTemplate = mongoTemplate;
        this.apkStorageLocation = Paths.get(apksDir).toAbsolutePath().normalize();
        this.iconStorageLocation = Paths.get(iconsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.apkStorageLocation);
            Files.createDirectories(this.iconStorageLocation);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create storage directories.", ex);
        }
    }

    public Apk processAndSaveApk(MultipartFile file, String organization, String group) throws IOException {
        // 1. Save uploaded file to a temporary location first.
        Path tempFile = Files.createTempFile("iems-upload-", ".apk");
        try {
            file.transferTo(tempFile);

            Apk apk = new Apk();
            String packageName;
            long versionCode;
            String versionName;
            String iconExtension = ".png"; // Default extension

            // 2. Parse metadata from the temporary file.
            Path savedIconPath = null;
            try (ApkFile apkFile = new ApkFile(tempFile.toFile())) {
                ApkMeta defaultApkMeta = apkFile.getApkMeta();
                packageName = defaultApkMeta.getPackageName();
                versionCode = defaultApkMeta.getVersionCode();
                versionName = defaultApkMeta.getVersionName();

                apk.setPackageName(packageName);
                apk.setVersionName(versionName);
                apk.setVersionCode(versionCode);

                // --- Multi-language Labels ---
                Map<String, String> localizedLabels = new HashMap<>();
                localizedLabels.put("default", defaultApkMeta.getLabel());
                for (Locale locale : apkFile.getLocales()) {
                    apkFile.setPreferredLocale(locale);
                    String label = apkFile.getApkMeta().getLabel();
                    if (label != null) {
                        localizedLabels.put(locale.toLanguageTag(), label);
                    }
                }
                apk.setLabels(localizedLabels);

                // 2.1 Check if packageName already exists in DB -> if yes, remove uploaded files and throw
                boolean exists = mongoTemplate.exists(Query.query(Criteria.where("packageName").is(packageName)), Apk.class);
                if (exists) {
                    // remove saved icon if any
                    if (savedIconPath != null) {
                        try { Files.deleteIfExists(savedIconPath); } catch (IOException ignored) {}
                    }
                    throw new IllegalStateException("Apk已存在: " + packageName);
                }
                // --- Icon Extraction ---
                Icon icon = apkFile.getIconFile();
                if (icon != null) {
                    String iconPath = icon.getPath();
                    int dotIndex = iconPath.lastIndexOf('.');
                    if (dotIndex > 0) {
                        iconExtension = iconPath.substring(dotIndex);
                    }
                    // Use a unique name for the icon file
                    String iconFileName = String.format("%s-v%d%s", packageName, versionCode, iconExtension);
                    Path iconTargetPath = this.iconStorageLocation.resolve(iconFileName).normalize();
                    Files.write(iconTargetPath, icon.getData());
                    savedIconPath = iconTargetPath;
                    apk.setIconPath(iconStorageLocation.getParent().relativize(iconTargetPath).toString().replace('\\', '/'));
                }
            }

            

            // 3. Move APK to its final destination with a unique name.
            String apkFileName = String.format("%s-v%d.apk", packageName, versionCode);
            Path apkTargetPath = this.apkStorageLocation.resolve(apkFileName).normalize();
            Files.move(tempFile, apkTargetPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Set file path after moving
            apk.setFilePath(apkStorageLocation.getParent().relativize(apkTargetPath).toString().replace('\\', '/'));

            // 4. Calculate hash from the final file.
            try (var fis = Files.newInputStream(apkTargetPath)) {
                apk.setFileHash(DigestUtils.sha256Hex(fis));
            }

            // 5. Set other info and save to database.
            apk.setOrganization(organization);
            apk.setGroup(group);
            return apkRepository.save(apk);

        } finally {
            // Ensure temporary file is deleted even if an error occurs.
            Files.deleteIfExists(tempFile);
        }
    }

    public List<Apk> getAllApks() {
        return apkRepository.findAll();
    }

    public Optional<Apk> findById(String id) {
        return apkRepository.findById(id);
    }

    public List<Apk> queryApks(ApkSearchCriteria criteria) {
        Query query = new Query();
        boolean isFuzzy = criteria.fuzzy() != null && criteria.fuzzy();

        if (StringUtils.hasText(criteria.packageName())) {
            if (isFuzzy) {
                query.addCriteria(Criteria.where("packageName").regex(criteria.packageName(), "i"));
            } else {
                query.addCriteria(Criteria.where("packageName").is(criteria.packageName()));
            }
        }
        if (StringUtils.hasText(criteria.versionName())) {
            if (isFuzzy) {
                query.addCriteria(Criteria.where("versionName").regex(criteria.versionName(), "i"));
            } else {
                query.addCriteria(Criteria.where("versionName").is(criteria.versionName()));
            }
        }
        if (StringUtils.hasText(criteria.organization())) {
            if (isFuzzy) {
                query.addCriteria(Criteria.where("organization").regex(criteria.organization(), "i"));
            } else {
                query.addCriteria(Criteria.where("organization").is(criteria.organization()));
            }
        }
        if (StringUtils.hasText(criteria.group())) {
            if (isFuzzy) {
                query.addCriteria(Criteria.where("group").regex(criteria.group(), "i"));
            } else {
                query.addCriteria(Criteria.where("group").is(criteria.group()));
            }
        }
        
        // 使用 limit+1 来判断是否有下一页，但返回给前端的仍然是 limit 数量
        Pageable pageable = PageRequest.of(criteria.offset() / criteria.limit(), criteria.limit());
        query.with(pageable);

        return mongoTemplate.find(query, Apk.class);
    }

    public Apk updateApk(String id, ApkUpdateRequest updateRequest) {
        Apk existingApk = apkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Apk not found with id: " + id));

        existingApk.setOrganization(updateRequest.organization());
        existingApk.setGroup(updateRequest.group());

        return apkRepository.save(existingApk);
    }

    public void deleteApk(String id) throws IOException {
        Apk apk = apkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Apk not found with id: " + id));

        // Delete files from storage
        if (StringUtils.hasText(apk.getFilePath())) {
            Path apkPath = apkStorageLocation.getParent().resolve(apk.getFilePath()).normalize();
            Files.deleteIfExists(apkPath);
        }
        if (StringUtils.hasText(apk.getIconPath())) {
            Path iconPath = iconStorageLocation.getParent().resolve(apk.getIconPath()).normalize();
            Files.deleteIfExists(iconPath);
        }

        // Delete record from database
        apkRepository.deleteById(id);
    }
}
