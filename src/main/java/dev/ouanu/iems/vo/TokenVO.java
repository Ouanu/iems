package dev.ouanu.iems.vo;

import java.io.Serializable;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenVO implements Serializable{
    private String accessToken;
    private String refreshToken;
    private Date expiresAt;
}
