package dev.ouanu.iems.dto;

import java.util.List;

import javax.validation.constraints.NotEmpty;

import org.springframework.util.StringUtils;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BatchUpdateApksRequest {

    @NotEmpty(message = "批量更新的ID列表不能为空")
    private List<String> ids;

    private String organization;

    private String group;

    public boolean hasUpdates() {
        return StringUtils.hasText(organization) || StringUtils.hasText(group);
    }

    public void normalize() {
        if (organization != null) {
            String trimmedOrganization = organization.trim();
            this.organization = StringUtils.hasText(trimmedOrganization) ? trimmedOrganization : null;
        }
        if (group != null) {
            String trimmedGroup = group.trim();
            this.group = StringUtils.hasText(trimmedGroup) ? trimmedGroup : null;
        }
    }
}
