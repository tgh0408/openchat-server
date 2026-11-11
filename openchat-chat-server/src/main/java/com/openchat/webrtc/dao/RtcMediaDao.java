package com.openchat.webrtc.dao;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.openchat.webrtc.domain.entity.RtcMediaSession;
import com.openchat.webrtc.domain.enums.RtcMediaTransportEnum;
import com.openchat.webrtc.mapper.RtcMediaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 媒体协商单表访问；实体版本拒绝旧版本及回退后的迟到请求。 */
@Repository
@RequiredArgsConstructor
public class RtcMediaDao {
    private final RtcMediaMapper mapper;

    /** 读取协商快照，更新时由实体版本控制。 */
    public RtcMediaSession selectById(String callId) {
        return mapper.selectById(callId);
    }

    /** 初始化协商，主键冲突仍交由数据库拒绝。 */
    public int insert(String callId, RtcMediaTransportEnum transport) {
        RtcMediaSession session = new RtcMediaSession();
        session.setCallId(callId);
        session.setTransport(transport);
        return mapper.insert(session);
    }

    /** 仅允许当前版本首次提交 offer。 */
    public int offer(String callId, long version, String offerId, String sdp) {
        RtcMediaSession session = currentP2p(callId, version);
        if (session == null || session.getOfferId() != null) return 0;
        session.setOfferId(offerId);
        session.setOfferSdp(sdp);
        return mapper.updateById(session);
    }

    /** 应答必须绑定当前 offer，不能覆盖已有应答。 */
    public int answer(String callId, long version, String offerId, String sdp) {
        RtcMediaSession session = currentP2p(callId, version);
        if (session == null || !java.util.Objects.equals(session.getOfferId(), offerId)
                || session.getAnswerSdp() != null) return 0;
        session.setAnswerSdp(sdp);
        return mapper.updateById(session);
    }

    /** 原子切换至 SFU 或清理其历史残留，保留版本条件避免旧节点写回 P2P。 */
    public int fallback(String callId, long version) {
        RtcMediaSession session = mapper.selectById(callId);
        if (session == null || !java.util.Objects.equals(session.getVersion(), Math.toIntExact(version))) return 0;
        var condition = new LambdaUpdateWrapper<RtcMediaSession>()
                .eq(RtcMediaSession::getCallId, callId)
                .eq(RtcMediaSession::getTransport, session.getTransport())
                // 实体的默认更新策略忽略 null，必须显式写入 SQL NULL。
                .set(RtcMediaSession::getOfferId, null)
                .set(RtcMediaSession::getOfferSdp, null)
                .set(RtcMediaSession::getAnswerSdp, null);
        session.setTransport(RtcMediaTransportEnum.SFU);
        session.setOfferId(null);
        session.setOfferSdp(null);
        session.setAnswerSdp(null);
        return mapper.update(session, condition);
    }

    /** 仅在呼叫终态条件更新成功后，由同一事务清理协商。 */
    public int deleteByCallId(String callId) {
        return mapper.deleteById(callId);
    }

    /** 读取当前实体并交给乐观锁插件按主键和版本更新。 */
    private RtcMediaSession currentP2p(String callId, long version) {
        RtcMediaSession session = mapper.selectById(callId);
        if (session == null || session.getTransport() != RtcMediaTransportEnum.P2P
                || !java.util.Objects.equals(session.getVersion(), Math.toIntExact(version))) return null;
        return session;
    }
}
