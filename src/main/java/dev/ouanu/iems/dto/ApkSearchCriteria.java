package dev.ouanu.iems.dto;

public record ApkSearchCriteria(
    String packageName,
    String versionName,
    String organization,
    String group,
    Boolean fuzzy, // 添加此字段
    int offset,
    int limit
) {}