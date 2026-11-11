package com.openchat.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.openchat.user.domain.enums.AddUserFunction;
import com.openchat.user.domain.enums.GenderEnum;
import com.openchat.user.domain.enums.UserActiveStatusEnum;
import com.openchat.common.domain.enums.YesOrNoEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 用户表
 * </p>
 *
 * @author <a  >TGH</a>
 * @since 2023-11-05
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "user",autoResultMap = true) //这个这是可以更新Mysql中的json格式的一个字段
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户昵称
     */
    @TableField("name")
    private String name;

    /** 仅通过名称写入入口更新，避免其他局部更新清空或回写旧排序键。 */
    @JsonIgnore
    @TableField(value = "name_sort_key", updateStrategy = FieldStrategy.NEVER)
    private String nameSortKey;

    @TableField("email")
    private String email;

    @TableField("phone")
    private Long phone;

    /**
     * 密码
     */
    @TableField("password")
    private String password;

    /**
     * 用户头像
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 性别 1为男性，0为女性
     */
    @TableField("sex")
    private GenderEnum sex;

    /**
     * OpenChatopenid用户标识
     */
    @TableField("open_id")
    private String openId;

    /**
     * 1需要通过申请 2直接添加我好友
     */
    @TableField("add_function")
    private AddUserFunction addFunction;

    /**
     * 个性签名
     */
    @TableField("personality_signature")
    private String personalitySignature;

    /**
     * 在线状态 1在线 2离线
     */
    @TableField("active_status")
    private UserActiveStatusEnum activeStatus;

    /**
     * 最后上下线时间
     */
    @TableField("last_opt_time")
    private Date lastOptTime;

    /**
     * ip信息
     */
    @TableField(value = "ip_info",typeHandler = Jackson3TypeHandler.class)
    private IpInfo ipInfo;

    /**
     * 使用状态 0.正常 1拉黑
     */
    @TableField("status")
    private YesOrNoEnum status;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField("update_time")
    private Date updateTime;

    /**
     * 前端数据库密码
     */
    @TableField("client_sql_password")
    private String clientSqlPassword;


    public void refreshIp(String ip) {
        if (this.ipInfo == null){
            ipInfo = new IpInfo();
        }
        ipInfo.refreshIp(ip);
    }
}
