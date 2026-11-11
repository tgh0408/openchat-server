package com.openchat.chat.mapper;

import com.openchat.chat.domain.entity.GroupMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 群成员表 Mapper 接口
 * </p>
 *
 * @author TGH
 * @since 2023-12-16
 */
public interface GroupMemberMapper extends BaseMapper<GroupMember> {

    /** 按房间和用户唯一键新增或恢复，不校验预期版本，已在群内的成员保持不变。 */
    int insertOrRestore(@Param("member") GroupMember member);

}
