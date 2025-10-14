package dev.ouanu.iems.config;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import dev.ouanu.iems.constant.AccountType;
import dev.ouanu.iems.constant.BizType;
import dev.ouanu.iems.constant.Permission;
import dev.ouanu.iems.entity.IdPermission;
import dev.ouanu.iems.entity.Operator;
import dev.ouanu.iems.mapper.DeviceMapper;
import dev.ouanu.iems.mapper.OperatorMapper;
import dev.ouanu.iems.mapper.PermissionMapper;
import dev.ouanu.iems.mapper.SnowflakeIdMapper;
import dev.ouanu.iems.service.SnowflakeIdService;

@Component
public class DatabaseInitializationRunner implements ApplicationRunner {

    private final OperatorMapper operatorMapper;
    private final PermissionMapper permissionMapper;
    private final DeviceMapper deviceMapper;
    private final SnowflakeIdMapper snowflakeIdMapper;
    private final SnowflakeIdService snowflakeIdService;
    private final Logger log = LoggerFactory.getLogger(DatabaseInitializationRunner.class);

    @Value("${app.initial.admin.email:admin@example.com}")
    private String adminEmail;

    @Value("${app.initial.admin.password:rxadmin8080}")
    private String adminPassword;

    @Value("${app.initial.admin.display-name:Administrator}")
    private String adminDisplayName;

    @Value("${app.initial.admin.phone:12345678910}")
    private String adminPhone;

    public DatabaseInitializationRunner(OperatorMapper operatorMapper, PermissionMapper permissionMapper, DeviceMapper deviceMapper, SnowflakeIdMapper snowflakeIdMapper, SnowflakeIdService snowflakeIdService) {
        this.operatorMapper = operatorMapper;
        this.permissionMapper = permissionMapper;
        this.deviceMapper = deviceMapper;
        this.snowflakeIdMapper = snowflakeIdMapper;
        this.snowflakeIdService = snowflakeIdService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureTablesExist();
            ensureDefaultAdmin();
        } catch (Exception ex) {
            log.error("Failed to initialize database / default admin", ex);
        }
    }

    private void ensureTablesExist() {
        operatorMapper.createTableIfNotExists();
        log.info("Ensured operators table exists (createTableIfNotExists executed).");

        permissionMapper.createTableIfNotExists();
        log.info("Ensured operator_permissions table exists.");

        deviceMapper.createTableIfNotExists();
        log.info("Ensured devices table exists.");

        snowflakeIdMapper.createTableIfNotExists();
        log.info("Ensured snowflake_ids table exists.");
    }

    private void ensureDefaultAdmin() {
        boolean exists = operatorMapper.existsByEmail(adminEmail);
        if (exists) {
            log.info("Admin user already exists (email={})", adminEmail);
            return;
        }

        String hashed = new BCryptPasswordEncoder().encode(adminPassword);
        Operator admin = buildDefaultAdmin(hashed);

        int inserted = operatorMapper.insert(admin);
        log.info("Inserted default admin (email={}), insert result={}", adminEmail, inserted);

        if (inserted > 0) {
            assignDefaultAdminPermissions(admin);
        }
    }

    private Operator buildDefaultAdmin(String hashedPassword) {
        Operator admin = new Operator();
        admin.setId(snowflakeIdService.nextIdAndPersist(BizType.OPERATOR));
        admin.setUuid(UUID.randomUUID().toString());
        admin.setPasswordHash(hashedPassword);
        admin.setDisplayName(adminDisplayName);
        admin.setPhone(adminPhone);
        admin.setEmail(adminEmail);
        admin.setAccountType(AccountType.ADMIN.name());
        admin.setActive(Boolean.TRUE);
        return admin;
    }

    private void assignDefaultAdminPermissions(Operator admin) {
        try {
            IdPermission adminPerm = new IdPermission();
            adminPerm.setId(admin.getId());
            // assign high level permissions — Permission enum contains detailed types
            adminPerm.setPermissions(Permission.APP, Permission.DEVICE, Permission.OPERATOR, Permission.ROM);
            permissionMapper.insert(adminPerm);
            log.info("Assigned SUPER_ADMIN permission to default admin");
        } catch (Exception e) {
            log.warn("Failed to assign permissions to admin: {}", e.getMessage());
        }
    }
}