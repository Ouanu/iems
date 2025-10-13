package dev.ouanu.iems.constant;

import java.util.Set;

public enum Permission {
    // These permissions are used for operator managing the system.
    OPERATOR("operator:read", "operator:write", "operator:delete", "operator:manage"),
    DEVICE("device:read", "device:write", "device:delete", "device:manage"),
    APP("app:read", "app:write", "app:delete", "app:manage"),
    ROM("rom:read", "rom:write", "rom:delete", "rom:manage"),
    AUTH("auth"),
    OPERATOR_READ("operator:read"),
    OPERATOR_WRITE("operator:write"),
    OPERATOR_DELETE("operator:delete"),
    OPERATOR_MANAGE("operator:manage"),
    DEVICE_READ("device:read"),
    DEVICE_WRITE("device:write"),
    DEVICE_DELETE("device:delete"),
    DEVICE_MANAGE("device:manage"),
    APP_READ("app:read"),
    APP_WRITE("app:write"),
    APP_DELETE("app:delete"),
    APP_MANAGE("app:manage"),
    ROM_READ("rom:read"),
    ROM_WRITE("rom:write"),
    ROM_DELETE("rom:delete"),
    ROM_MANAGE("rom:manage"),

    // These permissions are used for device managing its own data.
    DEVICE_WRITE_ITSELF("device:write:self"),
    DEVICE_UPDATE_ITSELF("device:update:self"),
    DEVICE_READ_ITSELF("device:read:self");

    private final Set<String> types;

    Permission(String... type) {
        this.types = Set.of(type);
    }

    public String[] getTypes() {
        return types.toArray(String[]::new);
    }

    public static Permission fromType(String type) {
        for (Permission p : Permission.values()) {
            if (p.types.contains(type)) {
                return p;
            }
        }
        throw new IllegalArgumentException("No enum constant for type: " + type);
    }

}
