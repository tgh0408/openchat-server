package com.openchat.user.dao;

import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.user.domain.entity.UserApply;
import com.openchat.user.domain.dto.ApplyChat;
import com.openchat.user.domain.enums.ApplyReadEnum;
import com.openchat.user.domain.enums.ApplyStatusEnum;
import com.openchat.user.domain.enums.ApplyTypeEnum;
import com.openchat.user.mapper.UserApplyMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.openchat.common.domain.vo.req.PageBaseReq;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;


/**
 * <p>
 * 用户申请表 服务实现类
 * </p>
 *
 * @author TGH
 * @since 2023-12-11
 */
@Repository
@RequiredArgsConstructor
public class UserApplyDao {
    private final UserApplyMapper userApplyMapper;

    /** 保存申请并保留主键回填，后续状态转换使用带条件的更新。 */
    public boolean save(UserApply apply) {
        return userApplyMapper.insert(apply) > 0;
    }

    //发起人是我，同意人是他
    public UserApply getByIdApplyId(Long uid, Long targetId) {
        return userApplyMapper.selectOne(new LambdaQueryWrapper<UserApply>().eq(UserApply::getUid,uid)
                .eq(UserApply::getTargetId,targetId)
                .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND)
                .eq(UserApply::getStatus, ApplyStatusEnum.WAIT_APPROVE)
                .orderByDesc(UserApply::getCreateTime)
                .last("limit 1"));
    }

    /** 收到的申请及发出申请的新回复按本人方向统计，每条待审批申请只计一次。 */
    public List<UserApply> getUnreadApplies(Long uid) {
        return userApplyMapper.selectList(new LambdaQueryWrapper<UserApply>()
                .and(query -> query
                        .and(received -> received
                                .eq(UserApply::getTargetId, uid)
                                .eq(UserApply::getReadStatus, ApplyReadEnum.UNREAD)
                                .eq(UserApply::getReceiverDeletedStatus, YesOrNoEnum.NO))
                        .or(sent -> sent
                                .eq(UserApply::getUid, uid)
                                .eq(UserApply::getSenderReadStatus, ApplyReadEnum.UNREAD)
                                .eq(UserApply::getSenderDelete, YesOrNoEnum.NO)))
                .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND)
                .eq(UserApply::getStatus, ApplyStatusEnum.WAIT_APPROVE)
                .orderByDesc(UserApply::getCreateTime)
                .orderByDesc(UserApply::getId));
    }

    /** 只确认当前账号这一侧的未读；版本冲突失败，不能清除并发到达的新消息。 */
    public boolean readApply(Long uid, Long applyId) {
        UserApply apply = userApplyMapper.selectOne(new LambdaQueryWrapper<UserApply>()
                .eq(UserApply::getId, applyId)
                .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND)
                .and(query -> query
                        .and(received -> received
                                .eq(UserApply::getTargetId, uid)
                                .eq(UserApply::getReadStatus, ApplyReadEnum.UNREAD)
                                .eq(UserApply::getReceiverDeletedStatus, YesOrNoEnum.NO))
                        .or(sent -> sent
                                .eq(UserApply::getUid, uid)
                                .eq(UserApply::getSenderReadStatus, ApplyReadEnum.UNREAD)
                                .eq(UserApply::getSenderDelete, YesOrNoEnum.NO))));
        if (apply == null) return false;
        UserApply update = versionedPatch(apply);
        LambdaUpdateWrapper<UserApply> condition = new LambdaUpdateWrapper<UserApply>()
                .eq(UserApply::getId, applyId)
                .eq(UserApply::getUid, apply.getUid())
                .eq(UserApply::getTargetId, apply.getTargetId())
                .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND);
        if (uid.equals(apply.getTargetId())) {
            update.setReadStatus(ApplyReadEnum.READ);
            condition.eq(UserApply::getReadStatus, ApplyReadEnum.UNREAD);
        } else {
            update.setSenderReadStatus(ApplyReadEnum.READ);
            condition.eq(UserApply::getSenderReadStatus, ApplyReadEnum.UNREAD);
        }
        if (userApplyMapper.update(update, condition) != 1) {
            throw new BusinessException("好友申请状态已变化，请刷新后重试");
        }
        return true;
    }

    /** 双方只能回复本人可见的指定待审批记录，不能将旧申请 ID 替换为另一条申请。 */
    public UserApply getParticipantPending(Long uid, Long applyId) {
        return userApplyMapper.selectOne(new LambdaQueryWrapper<UserApply>()
                .eq(UserApply::getId, applyId)
                .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND)
                .eq(UserApply::getStatus, ApplyStatusEnum.WAIT_APPROVE)
                .and(query -> query
                        .and(received -> received
                                .eq(UserApply::getTargetId, uid)
                                .eq(UserApply::getReceiverDeletedStatus, YesOrNoEnum.NO))
                        .or(sent -> sent
                                .eq(UserApply::getUid, uid)
                                .eq(UserApply::getSenderDelete, YesOrNoEnum.NO))));
    }

    /** 追加消息与对方未读一次写入；原版本交由插件校验，保留本人阅读状态。 */
    public boolean appendPendingMessage(UserApply apply, ApplyChat message, boolean toReceiver) {
        List<ApplyChat> messages = new ArrayList<>(apply.getMsg() == null ? List.of() : apply.getMsg());
        messages.add(message);
        UserApply update = versionedPatch(apply);
        update.setMsg(messages);
        if (toReceiver) {
            update.setReadStatus(ApplyReadEnum.UNREAD);
        } else {
            update.setSenderReadStatus(ApplyReadEnum.UNREAD);
        }
        update.setUpdateTime(new Date());
        return userApplyMapper.update(update, new LambdaUpdateWrapper<UserApply>()
                .eq(UserApply::getId, apply.getId())
                .eq(UserApply::getUid, apply.getUid())
                .eq(UserApply::getTargetId, apply.getTargetId())
                .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND)
                .eq(UserApply::getStatus, ApplyStatusEnum.WAIT_APPROVE)) == 1;
    }


    /** 收到与发出的好友申请统一分页，类型约束同时作用于两个方向。 */
    public IPage<UserApply> friendApplyPage(Long uid, PageBaseReq request) {
        return userApplyMapper.selectPage(new Page<>(request.getPageNo(), request.getPageSize()),
                new LambdaQueryWrapper<UserApply>()
                .and(query -> query
                        .and(received -> received
                                .eq(UserApply::getTargetId, uid)
                                .eq(UserApply::getReceiverDeletedStatus, YesOrNoEnum.NO))
                        .or(sent -> sent
                                .eq(UserApply::getUid, uid)
                                .eq(UserApply::getSenderDelete, YesOrNoEnum.NO)))
                        .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND)
                        .isNotNull(UserApply::getMsg)
                        .orderByDesc(UserApply::getCreateTime)
                        .orderByDesc(UserApply::getId));
    }

    /** 查询本人发出的最新好友申请，供提交入口判断是否可以重发。 */
    public UserApply getLastApply(Long uid, Long targetId) {
        return userApplyMapper.selectOne(new LambdaQueryWrapper<UserApply>().eq(UserApply::getTargetId, targetId)
                .eq(UserApply::getUid,uid)
                .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND)
                .orderByDesc(UserApply::getCreateTime)
                .orderByDesc(UserApply::getId)
                .last("limit 1"));
    }

    /** 校验申请归属后，只允许当前方向最新的记录进入审批或拒绝流程。 */
    public UserApply getLastNeedApply(Long targetUid, Long applyId) {
        UserApply selected = userApplyMapper.selectOne(new LambdaQueryWrapper<UserApply>()
                .eq(UserApply::getId, applyId)
                .eq(UserApply::getTargetId, targetUid)
                .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND)
                .last("limit 1"));
        if (selected == null) {
            return new UserApply();
        }
        UserApply latest = userApplyMapper.selectOne(new LambdaQueryWrapper<UserApply>()
                .eq(UserApply::getUid, selected.getUid())
                .eq(UserApply::getTargetId, targetUid)
                .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND)
                .orderByDesc(UserApply::getCreateTime)
                .orderByDesc(UserApply::getId)
                .last("limit 1"));
        return latest != null && latest.getStatus() == ApplyStatusEnum.WAIT_APPROVE
                ? latest : new UserApply();
    }

    public boolean agree(UserApply apply) {
        return transitionPending(apply, ApplyStatusEnum.APPROVED);
    }

    public boolean refuseApply(UserApply apply) {
        return transitionPending(apply, ApplyStatusEnum.REJECTED);
    }

    /** 状态转换校验原双方身份及待审批状态，旧请求不能覆盖已有终态。 */
    private boolean transitionPending(UserApply apply, ApplyStatusEnum status) {
        UserApply update = versionedPatch(apply);
        update.setStatus(status);
        return userApplyMapper.update(update, new LambdaUpdateWrapper<UserApply>()
                .eq(UserApply::getId, apply.getId())
                .eq(UserApply::getUid, apply.getUid())
                .eq(UserApply::getTargetId, apply.getTargetId())
                .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND)
                .eq(UserApply::getStatus, ApplyStatusEnum.WAIT_APPROVE)) == 1;
    }

    /** 拉黑时逐条携带原版本结束待审批申请，任一冲突交由调用方事务回滚。 */
    public boolean blackUser(Long uid, Long targetUid) {
        List<UserApply> applies = userApplyMapper.selectList(new LambdaQueryWrapper<UserApply>()
                .eq(UserApply::getUid, targetUid)
                .eq(UserApply::getTargetId, uid)
                .eq(UserApply::getType, ApplyTypeEnum.ADD_FRIEND)
                .eq(UserApply::getStatus, ApplyStatusEnum.WAIT_APPROVE));
        for (UserApply apply : applies) {
            if (!refuseApply(apply)) {
                throw new BusinessException("好友申请状态已变化，请刷新后重试");
            }
        }
        return !applies.isEmpty();
    }

    /** 独立实体只写本次变更；版本缺失时拒绝更新，避免插件跳过乐观锁。 */
    private UserApply versionedPatch(UserApply apply) {
        if (apply.getId() == null || apply.getVersion() == null) {
            throw new BusinessException("好友申请版本无效，请刷新后重试");
        }
        UserApply update = new UserApply();
        update.setVersion(apply.getVersion());
        return update;
    }
}
