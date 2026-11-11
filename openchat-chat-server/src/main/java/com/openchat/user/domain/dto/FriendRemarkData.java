package com.openchat.user.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** user_friend.tags 内的备注扩展结构，不新增数据库字段。 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FriendRemarkData(List<String> tags, List<String> phones, String description, List<Long> images) {}
