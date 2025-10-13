package dev.ouanu.iems.dto;

import dev.ouanu.iems.constant.Permission;

public class PermissionDTO {
    private String permissions;

    public void setPermissions(Permission perms) {
        this.permissions = String.join(",", perms.getTypes());
    }

    public String getPermissions() {
        return permissions;
    }
}
