/*
 Navicat Premium Dump SQL

 Source Server         : example-mysql
 Source Server Type    : MySQL
 Source Server Version : 80036 (8.0.36)
 Source Host           : localhost:3306
 Source Schema         : openchat

 Target Server Type    : MySQL
 Target Server Version : 80036 (8.0.36)
 File Encoding         : 65001

 Date: 11/11/2026 14:41:11
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for client_release
-- ----------------------------
DROP TABLE IF EXISTS `client_release`;
CREATE TABLE `client_release`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '不可变发布 ID',
  `version_name` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端版本号',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '安装包文件名',
  `bucket_name` varchar(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发布时的 MinIO 桶',
  `object_key` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '桶内对象键，不含域名或签名',
  `file_size` bigint NOT NULL COMMENT '安装包字节数',
  `sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '小写十六进制 SHA-256',
  `update_info` json NOT NULL COMMENT '更新说明字符串数组',
  `published_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '数据库记录时间，不用于选择当前版本',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_client_release_version`(`version_name` ASC) USING BTREE,
  CONSTRAINT `ck_client_release_file_size` CHECK (`file_size` > 0),
  CONSTRAINT `ck_client_release_update_info` CHECK (json_type(`update_info`) = _utf8mb4'ARRAY')
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户端发布记录，只追加' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for contact
-- ----------------------------
DROP TABLE IF EXISTS `contact`;
CREATE TABLE `contact`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `uid` bigint NOT NULL COMMENT 'uid',
  `room_id` bigint NOT NULL COMMENT '房间id',
  `read_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '阅读到的时间',
  `active_time` datetime(3) NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '会话内消息最后更新的时间(只有普通会话需要维护，全员会话不需要维护)',
  `is_top` int NULL DEFAULT 0 COMMENT '0未置顶 1已置顶',
  `not_disturb` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '免打扰 0否 1 是',
  `last_delete_id` bigint NULL DEFAULT 0 COMMENT '已经删除的历史记录位置',
  `last_read_msg_id` bigint NULL DEFAULT 0 COMMENT '用户最新阅读的位置，用于计算未读数量',
  `last_hidden_msg_id` bigint NULL DEFAULT NULL COMMENT '隐藏会话时的消息id',
  `contact_state` int NOT NULL DEFAULT 0 COMMENT '0正常 1删除 2 隐藏',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` bigint NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_uid_room_id`(`room_id` ASC, `uid` ASC) USING BTREE,
  INDEX `idx_room_id_read_time`(`room_id` ASC, `read_time` ASC) USING BTREE,
  INDEX `uid_room_id`(`uid` ASC, `room_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 85317 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会话列表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for emoji
-- ----------------------------
DROP TABLE IF EXISTS `emoji`;
CREATE TABLE `emoji`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '表情地址',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '表描述',
  `sha_256` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'SHA-256加密做重复性比较',
  `create_id` bigint NOT NULL COMMENT '首次创建id',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `extra` json NULL COMMENT '拓展信息',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `sha_256`(`sha_256` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 68 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '表情包SHA-256单一存储' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for group_member
-- ----------------------------
DROP TABLE IF EXISTS `group_member`;
CREATE TABLE `group_member`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `room_id` bigint NULL DEFAULT NULL COMMENT '房间号',
  `uid` bigint NOT NULL COMMENT '成员uid',
  `role` int NOT NULL COMMENT '成员角色 1群主 2管理员 3普通成员',
  `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '成员群名称',
  `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '我对于该群的备注',
  `delete_status` int NOT NULL COMMENT '是否被群主删除,群解散后会全部置为1',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_room_user`(`room_id` ASC, `uid` ASC) USING BTREE COMMENT '唯一索引',
  UNIQUE INDEX `idx_user_room`(`uid` ASC, `room_id` ASC) USING BTREE COMMENT '根据用户查询有哪些群组'
) ENGINE = InnoDB AUTO_INCREMENT = 145 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '群成员表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for message
-- ----------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `room_id` bigint NOT NULL COMMENT '会话表id',
  `from_uid` bigint NOT NULL COMMENT '消息发送者uid',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '消息内容',
  `reply_msg_id` bigint NULL DEFAULT NULL COMMENT '回复的消息内容',
  `client_msg_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '前端客户端消息id',
  `status` int NOT NULL COMMENT '消息状态 0正常 1删除',
  `gap_count` int NULL DEFAULT NULL COMMENT '与回复的消息间隔多少条',
  `type` int NULL DEFAULT 1 COMMENT '消息类型 1正常文本 2.撤回消息 3.大文件内容占位 4.图片 5.视频 6.文件 7.音频 8.语音 9.表情 10.系统消息 11.RTC消息',
  `extra` json NULL COMMENT '扩展信息',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_room_id`(`room_id` DESC, `id` DESC) USING BTREE,
  INDEX `idx_room_user`(`room_id` DESC, `from_uid` DESC, `id` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2119784067193913347 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '消息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for message_mark
-- ----------------------------
DROP TABLE IF EXISTS `message_mark`;
CREATE TABLE `message_mark`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `msg_id` bigint NOT NULL COMMENT '消息表id',
  `uid` bigint NOT NULL COMMENT '标记人uid',
  `type` int NOT NULL COMMENT '标记类型 1.举报',
  `status` int NOT NULL COMMENT '消息状态 0正常 1取消',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '版本锁',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_msg_id`(`msg_id` ASC) USING BTREE,
  INDEX `idx_uid`(`uid` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_update_time`(`update_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 86 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '消息标记表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for message_resource_ref
-- ----------------------------
DROP TABLE IF EXISTS `message_resource_ref`;
CREATE TABLE `message_resource_ref`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `ref_type` tinyint NOT NULL COMMENT '1 message，2 message_user',
  `ref_message_id` bigint NOT NULL COMMENT '引用者消息id',
  `owner_uid` bigint NOT NULL COMMENT '资源所属账号，与对象路径中的 UID 一致',
  `source_type` tinyint NOT NULL COMMENT '1 message，2 message_user',
  `message_id` bigint UNSIGNED NOT NULL,
  `room_id` bigint NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '是否撤回',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_message_resource_ref_message`(`source_type` ASC, `message_id` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '消息到 OSS 对象的引用索引，记录了哪条消息引用了另一条消息(只记录存OSS的数据,普通消息的引用记录在message本身)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for message_user
-- ----------------------------
DROP TABLE IF EXISTS `message_user`;
CREATE TABLE `message_user`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `room_id` bigint NOT NULL COMMENT '会话表id',
  `uid` bigint NOT NULL COMMENT 'uid(谁的信箱)',
  `from_uid` bigint NOT NULL COMMENT '发送者uid',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '消息内容',
  `reply_msg_id` bigint NULL DEFAULT NULL COMMENT '回复的消息内容',
  `client_msg_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '前端客户端消息id',
  `status` int NOT NULL COMMENT '消息状态 0正常 1删除',
  `gap_count` int NULL DEFAULT NULL COMMENT '与回复的消息间隔多少条',
  `type` int NULL DEFAULT 1 COMMENT '消息类型 1正常文本 2.撤回消息 3.大文件内容占位 4.图片 5.视频 6.文件 7.音频 8.语音 9.表情 10 系统消息',
  `extra` json NULL COMMENT '扩展信息',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_room_id`(`room_id` DESC, `id` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2119678317790408707 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '消息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for room
-- ----------------------------
DROP TABLE IF EXISTS `room`;
CREATE TABLE `room`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `type` int NOT NULL COMMENT '房间类型 1群聊 2单聊 3文件助手',
  `hot_flag` int NULL DEFAULT 0 COMMENT '是否全员展示 0否 1是',
  `active_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '群最后消息的更新时间（热点群不需要写扩散，只更新这里）',
  `last_msg_id` bigint NULL DEFAULT 0 COMMENT '会话中的最后一条消息id',
  `ext_json` json NULL COMMENT '额外信息（根据不同类型房间有不同存储的东西）',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `owner_uid` bigint NULL DEFAULT NULL COMMENT '个人会话所属用户，普通房间为空',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_room_type_owner`(`type` ASC, `owner_uid` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_update_time`(`update_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 91 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '房间表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for room_friend
-- ----------------------------
DROP TABLE IF EXISTS `room_friend`;
CREATE TABLE `room_friend`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `room_id` bigint NOT NULL COMMENT '房间id',
  `uid1` bigint NOT NULL COMMENT 'uid1（更小的uid）',
  `uid2` bigint NOT NULL COMMENT 'uid2（更大的uid）',
  `room_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '房间key由两个uid拼接，先做排序uid1_uid2',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `room_key`(`room_key` ASC) USING BTREE,
  UNIQUE INDEX `idx_room_id`(`room_id` ASC, `uid1` ASC, `uid2` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 35 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '单聊房间表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for room_group
-- ----------------------------
DROP TABLE IF EXISTS `room_group`;
CREATE TABLE `room_group`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `uid` bigint NOT NULL DEFAULT 0 COMMENT '群主id',
  `room_id` bigint NOT NULL COMMENT '房间id',
  `name` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '群名称',
  `ext_json` json NULL COMMENT '额外信息（根据不同类型房间有不同存储的东西）',
  `delete_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-正常,1-删除)',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` bigint NOT NULL DEFAULT 0 COMMENT '版本锁',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_room_id`(`room_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_update_time`(`update_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '群聊房间表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for rtc_call
-- ----------------------------
DROP TABLE IF EXISTS `rtc_call`;
CREATE TABLE `rtc_call`  (
  `call_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'RTC 呼叫唯一标识，由服务端生成；客户端和跨节点事件均以此标识呼叫',
  `room_id` bigint NOT NULL COMMENT 'OpenChat 聊天房间 ID；必须是服务端已存在且当前用户有权限访问的真实房间',
  `caller_uid` bigint NOT NULL COMMENT '主叫用户 UID；身份来自当前登录 JWT，不接受请求体传入的 UID',
  `callee_uid` bigint NULL DEFAULT NULL COMMENT '单聊被叫UID；群通话固定为空',
  `media_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '媒体模式：AUDIO 表示音频通话，VIDEO 表示视频通话',
  `state` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '呼叫状态：RINGING、ACCEPTED、CONNECTED、REJECTED、CANCELED、ENDED、EXPIRED',
  `version` int NOT NULL DEFAULT 1 COMMENT '呼叫状态版本号；创建时为 1，每次成功状态转换递增，用于乐观并发控制',
  `client_request_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户端动作幂等请求 ID；与 caller_uid 共同保证创建请求重试不重复创建呼叫',
  `active_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '活动呼叫唯一键；当前为 room:{roomId}，活动期间占用，进入终态时置 NULL 释放房间',
  `expires_at` datetime(3) NULL DEFAULT NULL COMMENT '呼叫邀请过期时间；未接听邀请在创建后 60 秒过期，不限制已接通时长',
  `end_reason` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '终止原因或事件类型，如 RTC_CALL_REJECTED、RTC_CALL_CANCELED、RTC_CALL_ENDED、RTC_CALL_EXPIRED',
  `created_at` datetime(3) NOT NULL COMMENT '呼叫创建时间，使用数据库/服务端统一的毫秒精度时间',
  `updated_at` datetime(3) NOT NULL COMMENT '最后一次呼叫状态或业务字段更新时间',
  `ended_at` datetime(3) NULL DEFAULT NULL COMMENT '呼叫进入 REJECTED、CANCELED、ENDED 或 EXPIRED 终态的时间；活动状态为空',
  `accepted_at` datetime(3) NULL DEFAULT NULL COMMENT '被叫接受呼叫的时间；未接听时为空',
  `connected_at` datetime(3) NULL DEFAULT NULL COMMENT '媒体连接确认时间；由后续 LiveKit webhook 补写',
  `duration_seconds` bigint NULL DEFAULT NULL COMMENT '呼叫时长，优先按 connected_at 计算，未接入媒体回调时回退 accepted_at',
  `ended_by_uid` bigint NULL DEFAULT NULL COMMENT '触发终态动作的用户 UID；超时终止为空',
  `summary_message_id` bigint NULL DEFAULT NULL COMMENT '终态通话摘要消息 ID；条件写入保证幂等',
  `participant_devices` json NULL COMMENT '参与者UID到已认证terminal/deviceId的JSON归属，与接听状态同行提交',
  PRIMARY KEY (`call_id`) USING BTREE COMMENT '按呼叫 ID 唯一定位一条 RTC 呼叫记录',
  UNIQUE INDEX `uq_rtc_caller_request`(`caller_uid` ASC, `client_request_id` ASC) USING BTREE COMMENT '主叫与客户端请求 ID 唯一，防止同一创建请求重试产生重复呼叫',
  UNIQUE INDEX `uq_rtc_active_room`(`active_key` ASC) USING BTREE COMMENT '活动呼叫房间唯一；active_key 终态置空后允许同一房间再次发起呼叫',
  INDEX `idx_rtc_room_created`(`room_id` ASC, `created_at` ASC) USING BTREE COMMENT '按房间和创建时间查询呼叫记录',
  INDEX `idx_rtc_callee_state`(`callee_uid` ASC, `state` ASC, `created_at` ASC) USING BTREE COMMENT '按被叫、呼叫状态和创建时间查询待接听或历史呼叫',
  INDEX `idx_rtc_state_expires`(`state` ASC, `expires_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'OpenChat RTC 呼叫控制记录表；媒体连接由 LiveKit 管理' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for rtc_group_call_member
-- ----------------------------
DROP TABLE IF EXISTS `rtc_group_call_member`;
CREATE TABLE `rtc_group_call_member`  (
  `room_id` bigint NOT NULL COMMENT '群聊房间ID',
  `uid` bigint NOT NULL COMMENT '参与用户UID',
  `call_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属通话ID，更新和删除时必须校验',
  `terminal` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '参与设备平台：PC、PHONE、WEB',
  `device_id` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '已认证设备标识，64位十六进制摘要',
  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '本次业务加入时间，不代表媒体实际连接时间',
  PRIMARY KEY (`room_id`, `uid`) USING BTREE,
  INDEX `idx_rtc_group_member_call`(`call_id` ASC) USING BTREE,
  INDEX `idx_rtc_group_member_uid`(`uid` ASC, `call_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '群通话当前参与成员，已接受且尚未退出' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for rtc_group_call_member_history
-- ----------------------------
DROP TABLE IF EXISTS `rtc_group_call_member_history`;
CREATE TABLE `rtc_group_call_member_history`  (
  `call_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属通话ID',
  `uid` bigint NOT NULL COMMENT '参与用户UID',
  `room_id` bigint NOT NULL COMMENT '群聊房间ID',
  `state` tinyint NOT NULL COMMENT '成员状态：1邀请中、2已接受、3已拒绝、4邀请过期、5已离开',
  `terminal` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最近一次参与绑定的平台：PC、PHONE、WEB',
  `device_id` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最近一次参与绑定的设备摘要，未参与时为空',
  `version` int NOT NULL DEFAULT 1 COMMENT '成员乐观锁版本',
  `invited_at` datetime(3) NULL DEFAULT NULL COMMENT '邀请时间，未受邀主动加入时为空',
  `accepted_at` datetime(3) NULL DEFAULT NULL COMMENT '最近一次业务加入时间',
  `left_at` datetime(3) NULL DEFAULT NULL COMMENT '最近一次参与的离开时间，重入时清空',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '成员记录创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '成员记录更新时间',
  PRIMARY KEY (`call_id`, `uid`) USING BTREE,
  INDEX `idx_rtc_group_history_room`(`room_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_rtc_group_history_uid`(`uid` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '群通话成员状态与历史记录，通话结束后保留,(单次通话无论进入退出多少次也仅有一条记录)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for rtc_media_session
-- ----------------------------
DROP TABLE IF EXISTS `rtc_media_session`;
CREATE TABLE `rtc_media_session`  (
  `call_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '呼叫唯一标识，逻辑关联 rtc_call.call_id，每个呼叫仅保存一份媒体协商快照',
  `transport` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '媒体传输模式：P2P 为端到端直连，SFU 为 LiveKit 服务器转发；同一呼叫降级至 SFU 后不回切',
  `version` int NOT NULL DEFAULT 1 COMMENT '媒体快照版本号，独立于呼叫版本；保存 Offer、Answer 或切换至 SFU 时递增，用于并发条件更新',
  `offer_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '主叫 Offer 的 clientRequestId，用于匹配被叫 Answer；未协商或降级至 SFU 时为空',
  `offer_sdp` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '主叫会话描述，包含已收集的 ICE 候选；未提交或降级至 SFU 时为空，禁止记录到业务日志',
  `answer_sdp` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '被叫对当前 Offer 的会话描述，包含已收集的 ICE 候选；未应答或降级至 SFU 时为空，禁止记录到业务日志',
  PRIMARY KEY (`call_id`) USING BTREE COMMENT '按呼叫唯一定位媒体协商快照，保障跨节点创建唯一性',
  CONSTRAINT `chk_rtc_media_transport` CHECK (`transport` in (_utf8mb4'P2P',_utf8mb4'SFU'))
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '活动 RTC 呼叫的媒体协商快照，保存 P2P 协商及 SFU 降级状态；呼叫结束时删除，不存储音视频媒体数据' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for rtc_webhook_event
-- ----------------------------
DROP TABLE IF EXISTS `rtc_webhook_event`;
CREATE TABLE `rtc_webhook_event`  (
  `event_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'LiveKit 原始事件 ID；跨实例及重投的唯一依据',
  `call_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '关联 rtc_call.call_id',
  `event_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'room_started/participant_joined/participant_left/room_finished',
  `room_sid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'LiveKit 房间实例 SID；同名房间重建时不同',
  `participant_sid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL DEFAULT NULL COMMENT '成员连接 SID；房间事件为空',
  `uid` bigint NULL DEFAULT NULL COMMENT '经呼叫成员快照确认的 UID；房间事件为空',
  `occurred_at_seconds` bigint NOT NULL COMMENT 'LiveKit createdAt，Unix 秒，不使用回调到达时间替代',
  `room_created_at_seconds` bigint NULL DEFAULT NULL COMMENT 'LiveKit 房间 creationTime，Unix 秒；缺失或无效为空',
  `received_at` datetime(3) NOT NULL COMMENT '首次成功落库回调的本机接收时间',
  PRIMARY KEY (`event_id`) USING BTREE,
  INDEX `idx_rtc_webhook_call_time`(`call_id` ASC, `occurred_at_seconds` ASC) USING BTREE,
  INDEX `idx_rtc_webhook_participant`(`call_id` ASC, `room_sid` ASC, `participant_sid` ASC, `occurred_at_seconds` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = 'LiveKit 已验签媒体事件；只追加，不驱动通话终态' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for secure_invoke_record
-- ----------------------------
DROP TABLE IF EXISTS `secure_invoke_record`;
CREATE TABLE `secure_invoke_record`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `secure_invoke_json` json NOT NULL COMMENT '请求快照参数json',
  `status` tinyint NOT NULL COMMENT '状态 1待执行 2已失败',
  `next_retry_time` datetime(3) NOT NULL COMMENT '下一次重试的时间',
  `retry_times` int NOT NULL COMMENT '已经重试的次数',
  `max_retry_times` int NOT NULL COMMENT '最大重试次数',
  `fail_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '执行失败的堆栈',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_next_retry_time`(`next_retry_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 34024 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '本地消息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sensitive_word
-- ----------------------------
DROP TABLE IF EXISTS `sensitive_word`;
CREATE TABLE `sensitive_word`  (
  `word` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '敏感词',
  PRIMARY KEY (`word`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '敏感词库' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户id',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '账户id可用于登录',
  `phone` bigint NULL DEFAULT NULL COMMENT '手机号',
  `name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户昵称',
  `name_sort_key` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '公开名称拼音排序键 v1',
  `password` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '密码',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户头像',
  `sex` int NULL DEFAULT NULL COMMENT '性别 1为男性，0为女性',
  `open_id` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '微信openid用户标识',
  `client_sql_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户端sqlite密码',
  `add_function` int NULL DEFAULT 1 COMMENT '0 直接添加我好友1需要通过申请',
  `personality_signature` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '个性签名',
  `active_status` int NULL DEFAULT 2 COMMENT '在线状态 1在线 2离线',
  `last_opt_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后上下线时间',
  `ip_info` json NULL COMMENT 'ip信息',
  `status` int NULL DEFAULT 0 COMMENT '使用状态 0.正常 1注销',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_open_id`(`open_id` ASC) USING BTREE,
  UNIQUE INDEX `idx_phone`(`phone` ASC) USING BTREE,
  UNIQUE INDEX `idx_email`(`email` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10389 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_apply
-- ----------------------------
DROP TABLE IF EXISTS `user_apply`;
CREATE TABLE `user_apply`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `uid` bigint NOT NULL COMMENT '申请人uid',
  `type` int NOT NULL COMMENT '申请类型 1加好友 2加群',
  `target_id` bigint NOT NULL COMMENT '接收人uid',
  `msg` json NOT NULL COMMENT '申请信息',
  `status` int NOT NULL COMMENT '申请状态 1待审批 2同意 3拒绝',
  `read_status` int NOT NULL COMMENT '阅读状态 1未读 2已读',
  `sender_read_status` int NOT NULL DEFAULT 2 COMMENT '发送者是否已读 1 未读 2 已读 (对方临时回消息需要置为未读)',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `request_settings` json NULL COMMENT '申请人私有设置及预留权限',
  `sender_delete` bigint NULL DEFAULT 0 COMMENT '发送者删除标记',
  `receiver_deleted` bigint NULL DEFAULT 0 COMMENT '接收者删除标记',
  `version` int NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_target_id_uid_status`(`target_id` ASC, `uid` ASC, `status` ASC) USING BTREE,
  INDEX `idx_target_id`(`target_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_update_time`(`update_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 88 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户申请表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_emoji
-- ----------------------------
DROP TABLE IF EXISTS `user_emoji`;
CREATE TABLE `user_emoji`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `uid` bigint NOT NULL COMMENT '用户表ID',
  `emoji_id` bigint NOT NULL COMMENT '表情id 关联emoji表',
  `delete_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-正常,1-删除)',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `sort` bigint NOT NULL DEFAULT 10000 COMMENT '用户内排序，越小越靠前',
  `version` int NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `USER_EMOJI_ID`(`uid` ASC, `emoji_id` ASC) USING BTREE COMMENT '用户表情联合索引',
  INDEX `IDX_USER_EMOJIS_UID`(`uid` ASC) USING BTREE,
  INDEX `IDX_USER_EMOJI_SORT`(`uid` ASC, `delete_status` ASC, `sort` ASC, `emoji_id` ASC) USING BTREE,
  INDEX `IDX_USER_EMOJI_MIN_SORT`(`uid` ASC, `sort` ASC, `emoji_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 165 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表情包' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_friend
-- ----------------------------
DROP TABLE IF EXISTS `user_friend`;
CREATE TABLE `user_friend`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `uid` bigint NOT NULL COMMENT 'uid',
  `friend_uid` bigint NOT NULL DEFAULT 0 COMMENT '好友uid',
  `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '好友备注',
  `nickname_sort_key` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '私有备注拼音排序键 v1',
  `star` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标星朋友',
  `black_status` int NOT NULL DEFAULT 0 COMMENT '好友拉黑标记(0-正常,1-拉黑)',
  `delete_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-正常,1-删除)',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `tags` json NULL COMMENT '当前用户对该好友的私有标签',
  `version` int NOT NULL COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_friend_pair`(`uid` ASC, `friend_uid` ASC) USING BTREE,
  INDEX `idx_uid_friend_uid`(`uid` ASC, `friend_uid` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_update_time`(`update_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 87 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户联系人表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_role
-- ----------------------------
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `uid` bigint NOT NULL COMMENT 'uid',
  `role_id` bigint NOT NULL COMMENT '角色id',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_uid`(`uid` ASC) USING BTREE,
  INDEX `idx_role_id`(`role_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_update_time`(`update_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关系表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
