package dev.ouanu.iems.entity;

import java.io.Serializable;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class SnowflakeId implements Serializable{
    private Long id;
    private String type; // "operator" 或 "device"
    private String node;
    private Instant createdAt;

}
