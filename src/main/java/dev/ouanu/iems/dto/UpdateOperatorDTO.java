package dev.ouanu.iems.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateOperatorDTO {
    private String displayName;
    private String phone;
    private String email;
    private String accountType;
    private String department;
    private String team;
    private String position;
    private String level;
    private Boolean active;
}
