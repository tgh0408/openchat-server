package com.openchat.user.mapper;

import com.openchat.user.domain.dto.EmojiInfoDTO;
import com.openchat.user.domain.entity.UserEmoji;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 用户表情包 Mapper 接口
 * </p>
 *
 * @author TGH
 * @since 2024-01-03
 */
public interface UserEmojiMapper extends BaseMapper<UserEmoji> {

    List<EmojiInfoDTO> listByUid(@Param("uid") Long uid);

    UserEmoji selectFirstCollection(@Param("uid") Long uid);

}
