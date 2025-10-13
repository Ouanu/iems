package dev.ouanu.iems.constant;

public enum BizType {
    OPERATOR("operator"),
    DEVICE("device");

    private final String name;
    
    BizType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
