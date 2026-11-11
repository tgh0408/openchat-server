package com.openchat.user.mapper;

import com.openchat.user.domain.entity.UserFriend;
import com.openchat.user.domain.dto.FriendPageRow;
import com.openchat.user.domain.dto.FriendDetailRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 用户联系人表 Mapper 接口
 * </p>
 *
 * @author TGH
 * @since 2023-12-10
 */
public interface UserFriendMapper extends BaseMapper<UserFriend> {

    FriendDetailRow getFriendDetail(@Param("uid") Long uid, @Param("targetUid") Long targetUid);

    List<FriendPageRow> getFriendPage(@Param("uid") Long uid, @Param("sortKey") String sortKey,
                                    @Param("relationId") Long relationId, @Param("limit") int limit);
}
