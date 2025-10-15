package dev.ouanu.iems.dto;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUpdateOperatorsRequest {

    @NotEmpty(message = "批量更新的ID列表不能为空")
    private List<Long> ids;

    @Valid
    @NotNull(message = "updates对象不能为空")
    private UpdateOperatorDTO updates;
}
