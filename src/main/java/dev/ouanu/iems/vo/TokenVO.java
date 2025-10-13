package dev.ouanu.iems.vo;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenVO {
    private String accessToken;
    private String refreshToken;
    private Date expiresAt;
}
