package dev.ouanu.iems.entity;

import java.io.Serializable;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "apks")
public class Apk implements Serializable{
    private String id;
    private String packageName;
    private String iconPath;
    private String organization;
    private Map<String, String> labels;
    private String versionName;
    private Long versionCode;
    private String filePath;
    private String fileHash;
    private String group;

}
