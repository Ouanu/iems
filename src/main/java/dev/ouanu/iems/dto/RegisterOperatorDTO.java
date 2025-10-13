package dev.ouanu.iems.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.ouanu.iems.entity.Operator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterOperatorDTO {
    @NotBlank
    private String displayName;
    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank
    @Size(min = 8, max = 128)
    private String password;
    @NotBlank
    private String phone;
    private String email;
    @NotBlank
    private String accountType;
    private String department;
    private String team;
    private String position;
    private String level;
    private Boolean active = true;

    public Operator toEntity(PasswordEncoder passwordEncoder) {
        Operator operator = new Operator();
        operator.setDisplayName(this.displayName);
        operator.setPhone(this.phone);
        operator.setEmail(this.email);
        operator.setAccountType(this.accountType);
        operator.setPasswordHash(passwordEncoder.encode(this.password));
        operator.setDepartment(this.department);
        operator.setTeam(this.team);
        operator.setPosition(this.position);
        operator.setLevel(this.level);
        operator.setActive(this.active);
        return operator;
    }
}
