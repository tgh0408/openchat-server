package com.openchat.chat.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.openchat.chat.domain.entity.RoomGroup;
import lombok.AllArgsConstructor;
import lombok.Getter;

//群权限枚举
@AllArgsConstructor
@Getter
public enum GroupRoleEnum {
    SUPER_ADMIN("群主、超级管理员",1),
    ADMIN("管理员",2),
    NORMAL("群成员",3);

    private final String RoleName;
    @EnumValue
    @JsonValue
    private final Integer Role;

    public static boolean isNormal(GroupRoleEnum roleEnum) {
        return !isAdmin(roleEnum);
    }


    public static boolean isAdmin(GroupRoleEnum roleEnum){
        return roleEnum.Role < NORMAL.Role;
    }

    public static boolean isSuperAdmin(GroupRoleEnum roleEnum){
        return roleEnum == SUPER_ADMIN;
    }


}
