package com.openchat.user.domain.vo.req;

import com.openchat.common.domain.vo.req.CursorPageBaseReq;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.openchat.user.service.adapter.FriendPageCursor;

@Schema(description = "好友分页请求")
public class FriendPageReq extends CursorPageBaseReq {

    @Override
    @Size(max = FriendPageCursor.MAX_ENCODED_LENGTH)
    public String getCursor() {
        return super.getCursor();
    }

    @Override
    @NotNull
    @Min(1)
    public Integer getPageSize() {
        return super.getPageSize();
    }
}
