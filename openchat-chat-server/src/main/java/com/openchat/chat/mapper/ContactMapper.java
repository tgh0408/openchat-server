package com.openchat.chat.mapper;

import com.openchat.chat.domain.entity.Contact;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 会话列表 Mapper 接口
 * </p>
 *
 * @author TGH
 * @since 2023-12-14
 */
public interface ContactMapper extends BaseMapper<Contact> {

    boolean hasMessageAfter(@Param("uid") Long uid, @Param("roomId") Long roomId,
                            @Param("lastDeleteId") Long lastDeleteId, @Param("group") boolean group);

    void createMissingContacts(@Param("roomId") Long roomId, @Param("uids") List<Long> uids);

    List<Contact> getContactList(@Param("uid") Long uid);

    void saveOrRestore(Contact contact);
}
