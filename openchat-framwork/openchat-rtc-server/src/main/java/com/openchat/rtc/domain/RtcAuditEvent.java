package com.openchat.rtc.domain;

/**
 * RTC 审计日志事件，枚举名称作为稳定的日志检索标识。
 * 记录接听竞争、设备归属校验、恢复、结束及通知投递过程；SSE 传输事件另用 {@link RtcEventType}。
 * 提交类事件只表示数据库业务状态已经提交，不表示所有客户端已经收到通知或媒体已经断开。
 */
public enum RtcAuditEvent {
    /** 接听入口取得分布式锁后开始处理请求，记录当前用户、设备指纹及请求标识。 */
    RTC_ACCEPT_REQUESTED,

    /** 本次接听产生了新的状态转换，接听状态和设备归属已随事务提交。 */
    RTC_ACCEPT_COMMITTED,

    /** 本设备接听请求对应的事务已提交；包含同设备重复接听的幂等确认，不一定发生新状态转换。 */
    RTC_ACCEPT_CONFIRMED,

    /** 接听因其他设备已接听、邀请失效或设备归属异常等业务原因被拒绝，记录具体原因。 */
    RTC_ACCEPT_REJECTED,

    /** 在等待期限内未取得接听分布式锁，请求尚未进入接听事务，不代表其他设备已接听。 */
    RTC_LOCK_BUSY,

    /** 通话版本或数据库并发状态发生冲突，当前操作未能按预期完成，由客户端重新确认状态。 */
    RTC_STATE_CONFLICT,

    /** 收到用户主动恢复通话的请求，准备校验参与资格、当前状态与设备归属。 */
    RTC_RECOVERY_REQUESTED,

    /** 恢复事务已完成，或已在新事务中确认并发恢复结果；不表示客户端媒体连接已经建立。 */
    RTC_RECOVERY_COMMITTED,

    /** 恢复设备与保存的归属不一致，或归属缺失、无法确认；准备进入强制结束流程，尚未确认结束提交。 */
    RTC_RECOVERY_DEVICE_MISMATCH,

    /** 因恢复设备不匹配或归属缺失而强制结束通话的事务已提交，记录实际结束原因和最终版本。 */
    RTC_FORCED_END_COMMITTED,

    /** 结束通话时发生版本或数据库并发冲突，失败事务已回滚，记录当前失败次数并检查剩余重试额度。 */
    RTC_FORCED_END_RETRY,

    /** 结束通话的有限并发重试已耗尽，未能确认结束成功，保留异常供排查。 */
    RTC_FORCED_END_FAILED,

    /** 通知可靠任务已随业务事务提交；任务登记成功不等于消息已经发布到 MQ。 */
    RTC_NOTIFICATION_REGISTERED,

    /** RTC 通知的 MQ 发送调用已成功返回；不表示各节点已消费或客户端已收到通知。 */
    RTC_MQ_PUBLISHED,

    /** 一条 RTC 事件已成功写入本机某个 SSE 连接，不作为客户端接收或关闭窗口的确认。 */
    RTC_SSE_WRITE_COMPLETED,

    /** 向本机某个 SSE 连接写入 RTC 事件失败，随后按断线处理移除该连接。 */
    RTC_SSE_WRITE_FAILED,

    /** 已结束通话对应的 LiveKit 房间删除成功，或服务端返回房间不存在；不表示 P2P 媒体已断开。 */
    RTC_MEDIA_CLEANUP_COMPLETED,

    /** 本次 LiveKit 房间清理失败，记录异常类型并抛出异常，交由既有可靠调用机制处理重试。 */
    RTC_MEDIA_CLEANUP_FAILED,

    /** 结束请求对应的事务已完成并返回终态；也可能是对已有终态的幂等确认，不一定是本次新结束。 */
    RTC_END_CONFIRMED,

    /** RTC 通知的必需字段无效或扩展载荷无法解析，丢弃该通知，不继续向 SSE 投递。 */
    RTC_NOTIFICATION_INVALID,

    /** RTC 通知已通过格式校验，开始交给本机 SSE 管理器分发，不代表存在连接或已写入成功。 */
    RTC_SSE_DISPATCH,

    /** RTC 业务操作异常进入统一响应映射，记录业务原因与返回错误码，供关联客户端失败结果。 */
    RTC_OPERATION_REJECTED
}
