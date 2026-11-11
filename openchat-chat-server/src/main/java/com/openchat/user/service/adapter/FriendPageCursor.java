package com.openchat.user.service.adapter;

import com.openchat.common.exception.BusinessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;

/** 游标只描述排序位置，不提供授权；SQL 始终独立限定当前用户。 */
public record FriendPageCursor(int version, String ownerUid, String sortKey, String relationId) {
    public static final int MAX_ENCODED_LENGTH = 16384;
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public static String encode(Long uid, String key, Long id) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MAPPER.writeValueAsBytes(new FriendPageCursor(1, uid.toString(), key, id.toString())));
        } catch (JacksonException e) {
            throw new IllegalStateException("好友游标编码失败", e);
        }
    }

    public static FriendPageCursor decode(Long uid, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            if (value.length() > MAX_ENCODED_LENGTH) {
                throw new IllegalArgumentException();
            }
            FriendPageCursor cursor = MAPPER.readValue(
                    Base64.getUrlDecoder().decode(value), FriendPageCursor.class);
            if (cursor == null || cursor.version != 1 || !uid.toString().equals(cursor.ownerUid)
                    || !NameSortKey.isValid(cursor.sortKey)
                    || cursor.relationId == null || !cursor.relationId.matches("[1-9][0-9]{0,18}")
                    || Long.parseLong(cursor.relationId) <= 0) {
                throw new IllegalArgumentException();
            }
            return cursor;
        } catch (JacksonException | IllegalArgumentException e) {
            throw new BusinessException("好友分页游标已失效或无效，请从首页重新加载");
        }
    }

    public Long id() {
        return Long.valueOf(relationId);
    }
}
