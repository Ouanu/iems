package dev.ouanu.iems.dto;

import java.util.List;

import javax.validation.constraints.NotEmpty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUpdateDevicesRequest {

    @NotEmpty(message = "批量更新的ID列表不能为空")
    private List<Long> ids;

    private Boolean active;

    private Boolean locked;
}
