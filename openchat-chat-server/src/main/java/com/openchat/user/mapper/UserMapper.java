package com.openchat.user.mapper;

import com.openchat.user.domain.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 用户表 Mapper 接口
 * </p>
 *
 * @author <a  >TGH</a>
 * @since 2023-11-05
 */
public interface UserMapper extends BaseMapper<User> {
    int updateConnectionIp(@Param("uid") Long uid, @Param("ip") String ip);

    int fillCreateIpDetail(@Param("uid") Long uid, @Param("ip") String ip, @Param("detail") String detail);

    int fillUpdateIpDetail(@Param("uid") Long uid, @Param("ip") String ip, @Param("detail") String detail);
}
