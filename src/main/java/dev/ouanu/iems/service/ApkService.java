package dev.ouanu.iems.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import dev.ouanu.iems.dto.ApkSearchCriteria;
import dev.ouanu.iems.dto.ApkUpdateRequest;
import dev.ouanu.iems.entity.Apk;
import dev.ouanu.iems.repository.ApkRepository;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.IconFace;

@Service
public class ApkService {
    private final ApkRepository apkRepository;
    private final Path apkStorageLocation;
    private final Path iconStorageLocation;
    private final MongoTemplate mongoTemplate;

    private static final String FIELD_PACKAGE_NAME = "packageName";
    private static final String FIELD_APP_NAME = "labels.default";
    private static final String FIELD_VERSION_NAME = "versionName";
    private static final String FIELD_ORGANIZATION = "organization";
    private static final String FIELD_GROUP = "group";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 200;
    private static final String APK_NOT_FOUND_MESSAGE = "Apk not found with id: ";

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

    @Cacheable(value = "apks:all", key = "'all'")
    public List<Apk> getAllApks() {
        return apkRepository.findAll();
    }

    @Cacheable(value = "apks:byId", key = "#id")
    public Optional<Apk> findById(String id) {
        return apkRepository.findById(id);
    }

    @Cacheable(value = "apks:query", key = "T(java.lang.String).valueOf(#criteria.hashCode())")
    public List<Apk> queryApks(ApkSearchCriteria criteria) {
        Query query = new Query();
        boolean isFuzzy = criteria.fuzzy() != null && criteria.fuzzy();

        addCriteriaToQuery(query, FIELD_APP_NAME, criteria.appName(), isFuzzy);
        addCriteriaToQuery(query, FIELD_PACKAGE_NAME, criteria.packageName(), isFuzzy);
        addCriteriaToQuery(query, FIELD_VERSION_NAME, criteria.versionName(), isFuzzy);
        addCriteriaToQuery(query, FIELD_ORGANIZATION, criteria.organization(), isFuzzy);
        addCriteriaToQuery(query, FIELD_GROUP, criteria.group(), isFuzzy);

        int limit = criteria.limit();
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        } else if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        int offset = Math.max(criteria.offset(), 0);
        query.skip(offset);
        query.limit(limit);

        return mongoTemplate.find(query, Apk.class);
    }

    private void addCriteriaToQuery(Query query, String fieldName, String value, boolean isFuzzy) {
        if (StringUtils.hasText(value)) {
            if (isFuzzy) {
                query.addCriteria(Criteria.where(fieldName).regex(value, "i"));
            } else {
                query.addCriteria(Criteria.where(fieldName).is(value));
            }
        }
    }

    @Transactional
    @CacheEvict(value = { "apks:all", "apks:byId", "apks:query" }, allEntries = true)
    public void adminBatchUpdateApks(List<String> ids, String organization, String group) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("批量更新需要至少一个APK ID");
        }

        String normalizedOrganization = StringUtils.hasText(organization) ? organization.trim() : null;
        String normalizedGroup = StringUtils.hasText(group) ? group.trim() : null;

        if (normalizedOrganization == null && normalizedGroup == null) {
            throw new IllegalArgumentException("请至少选择一个更新字段");
        }

        Set<String> uniqueIds = new LinkedHashSet<>();
        for (String id : ids) {
            if (!StringUtils.hasText(id)) {
                throw new IllegalArgumentException("ID 不能为空");
            }
            uniqueIds.add(id.trim());
        }

        for (String id : uniqueIds) {
            Apk apk = apkRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException(APK_NOT_FOUND_MESSAGE + id));
            if (normalizedOrganization != null) {
                apk.setOrganization(normalizedOrganization);
            }
            if (normalizedGroup != null) {
                apk.setGroup(normalizedGroup);
            }
            apkRepository.save(apk);
        }
    }

    @CacheEvict(value = { "apks:all", "apks:byId", "apks:query" }, allEntries = true)
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

                // 2.1 Check if packageName already exists in DB -> if yes, remove uploaded
                // files and throw
                List<Apk> existingApks = mongoTemplate.find(Query.query(Criteria.where(FIELD_PACKAGE_NAME).is(packageName)), Apk.class);
                
                if (!existingApks.isEmpty()) {
                    if (versionCode == existingApks.get(0).getVersionCode()) {
                        throw new IllegalStateException("Apk已存在: " + packageName);
                    }else{
                        // Allow different versionCode for the same packageName
                    }
                }
                // --- Icon Extraction ---
                // IconFace iconFace = apkFile.getAllIcons().stream()
                //         .filter(IconFace::isFile)
                //         .max(Comparator.comparingInt(icon -> icon.getData().length))
                //         .orElse(null);
                int size = apkFile.getAllIcons().size();
                apkFile.getAllIcons().sort((a, b) -> Integer.compare(b.getData().length, a.getData().length));
                IconFace iconFace = apkFile.getAllIcons().get(size / 2);

                if (iconFace != null) {
                    String iconPath = iconFace.getPath();
                    int dotIndex = iconPath.lastIndexOf('.');
                    if (dotIndex > 0) {
                        iconExtension = iconPath.substring(dotIndex);
                    }
                    // Use a unique name for the icon file
                    String iconFileName = String.format("%s-v%d%s", packageName, versionCode, iconExtension);
                    Path iconTargetPath = this.iconStorageLocation.resolve(iconFileName).normalize();
                    Files.write(iconTargetPath, iconFace.getData());
                    apk.setIconPath(
                            iconStorageLocation.getParent().relativize(iconTargetPath).toString().replace('\\', '/'));
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

    @CacheEvict(value = { "apks:all", "apks:byId", "apks:query" }, allEntries = true)
    public Apk updateApk(String id, ApkUpdateRequest updateRequest) {
        Apk existingApk = apkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(APK_NOT_FOUND_MESSAGE + id));

        existingApk.setOrganization(updateRequest.organization());
        existingApk.setGroup(updateRequest.group());

        return apkRepository.save(existingApk);
    }

    @CacheEvict(value = { "apks:all", "apks:byId", "apks:query" }, allEntries = true)
    public void deleteApk(String id) throws IOException {
        deleteApkById(id);
    }

    @Transactional
    @CacheEvict(value = { "apks:all", "apks:byId", "apks:query" }, allEntries = true)
    public void deleteApks(List<String> ids) throws IOException {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("批量删除需要至少一个APK ID");
        }

        Set<String> uniqueIds = new LinkedHashSet<>();
        for (String id : ids) {
            if (!StringUtils.hasText(id)) {
                throw new IllegalArgumentException("ID 不能为空");
            }
            uniqueIds.add(id.trim());
        }

        for (String id : uniqueIds) {
            deleteApkById(id);
        }
    }

    private void deleteApkById(String id) throws IOException {
        Apk apk = apkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(APK_NOT_FOUND_MESSAGE + id));

        if (StringUtils.hasText(apk.getFilePath())) {
            Path apkPath = apkStorageLocation.getParent().resolve(apk.getFilePath()).normalize();
            Files.deleteIfExists(apkPath);
        }
        if (StringUtils.hasText(apk.getIconPath())) {
            Path iconPath = iconStorageLocation.getParent().resolve(apk.getIconPath()).normalize();
            Files.deleteIfExists(iconPath);
        }

        apkRepository.deleteById(id);
    }
}
