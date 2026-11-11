package com.openchat.chat.mapper;

import com.openchat.chat.domain.entity.Room;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 房间表 Mapper 接口
 * </p>
 *
 * @author TGH
 * @since 2023-12-13
 */
public interface RoomMapper extends BaseMapper<Room> {

    int promoteToHotIfNeeded(@Param("roomId") Long roomId, @Param("threshold") int threshold);

    void createFileAssistantIfAbsent(@Param("uid") Long uid);

}
