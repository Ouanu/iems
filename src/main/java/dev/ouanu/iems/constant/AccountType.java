package dev.ouanu.iems.constant;

public enum AccountType {
    ADMIN("Admin"),
    EMPLOYEE("Employee"),
    CONTRACTOR("Contractor"),
    PARTNER("Partner");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
