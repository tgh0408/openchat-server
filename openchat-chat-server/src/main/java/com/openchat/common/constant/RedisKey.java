package com.openchat.common.constant;

public class RedisKey {
    private static final String BASE_KEY = "openchat:chat";
    /**
     * 在线用户列表
     */
    public static final String ONLINE_UID_ZET = "online";

    /**
     * 离线用户列表
     */
    public static final String OFFLINE_UID_ZET = "offline";

    /**
     * 热门房间列表
     */
    public static final String HOT_ROOM_ZET = "hotRoom";

    /**
     * 用户信息
     */
    public static final String USER_INFO_STRING = "userInfo:uid_%d";

    /**
     * 房间好友信息
     */
    public static final String ROOM_FRIEND_STRING = "roomFriend:roomId_%d";

    /**
     * 房间详情
     */
    public static final String ROOM_INFO_STRING = "roomInfo:roomId_%d";

    // 共用哈希标签，保证 Lua 涉及的键在 Redis Cluster 中位于同一槽位。
    public static final String ROOM_ACTIVITY_PENDING = ":roomActivity:{roomActivity}:pending";
    public static final String ROOM_ACTIVITY_LATEST = ":roomActivity:{roomActivity}:room_%d";
    public static final String ROOM_ACTIVITY_FLUSH_LOCK = ":roomActivity:{roomActivity}:flushLock";

    /**
     * 群组详情
     */
    public static final String GROUP_INFO_STRING = "groupInfo:roomId_%d";

    /**
     * 群组好友关系
     */
    public static final String GROUP_MEMBER = "groupMember:roomId_%d";

    /**
     * 消息详情
     */
    public static final String MESSAGE_INFO_STRING = "messageInfo:msgId_%d";

    /**
     * 同账号、同平台唯一登录令牌；不再读取旧的单账号 key。
     */
    public static final String USER_TOKEN_STRING = "userToken:uid_%d:%s";

    /**
     * 管理后台登录会话
     */
    public static final String ADMIN_SESSION_STRING = "adminSession:token_%s";

    /**
     * 用户的信息更新时间
     */
    public static final String USER_MODIFY_STRING = "userModify:uid_%d";

    /**
     * 用户的信息汇总
     */
    public static final String USER_SUMMARY_STRING = "userSummary:uid_%d";

    /**
     * 用户GPT聊天次数
     */
    public static final String USER_CHAT_NUM = "useChatGPTNum:uid_%d";

    public static final String USER_CHAT_CONTEXT = "useChatGPTContext:uid_%d_roomId_%d";

    /**
     * 保存Open id
     */
    public static final String OPEN_ID_STRING = "openid:%s";


    /**
     * 用户上次使用GLM使用时间
     */
    public static final String USER_GLM2_TIME_LAST = "userGLM2UseTime:uid_%d";

    /**
     * 信令服务顺序主键
     */
    public static final String SIGNAL_STRING = "signal:%s";

    public static String getKey(String key,Object ...o){
        return BASE_KEY + String.format(key, o);
    }
}
