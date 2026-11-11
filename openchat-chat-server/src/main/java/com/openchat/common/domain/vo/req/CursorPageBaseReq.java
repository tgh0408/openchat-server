package com.openchat.common.domain.vo.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @author <a  > TGH</a>
 * @since 2023-03-19
 */
@Data
@Schema(description = "游标翻页请求")
@AllArgsConstructor
@NoArgsConstructor
public class CursorPageBaseReq {

    @Schema(description = "页面大小")
    @Min(value = 0, message = "无效的好友分页大小")
    @Max(value = 100, message = "无效的好友分页大小")
    @NonNull
    private Integer pageSize = 3;

    @Schema(description = "游标（初始为null，后续请求附带上次翻页的游标）")
    private String cursor;

    public Page plusPage() {
        return new Page<>(1, this.pageSize,false);
    }

    @JsonIgnore
    public Boolean isFirstPage() {
        return StringUtils.isEmpty(cursor);
    }
}
