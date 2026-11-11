package com.openchat.transaction.dao;

import com.openchat.transaction.domain.entity.SecureInvokeRecord;
import com.openchat.transaction.mapper.SecureInvokeRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/** Reliable invocation record access and lease operations. */
@Component
@RequiredArgsConstructor
public class SecureInvokeRecordDao {
    private final SecureInvokeRecordMapper secureInvokeRecordMapper;

    /** 保存可靠调用记录，保留主键回填及调用方事务。 */
    public boolean save(SecureInvokeRecord record) {
        return secureInvokeRecordMapper.insert(record) > 0;
    }

    public List<SecureInvokeRecord> getDueRecords(int limit) {
        Date now = new Date();
        return secureInvokeRecordMapper.selectList(new LambdaQueryWrapper<SecureInvokeRecord>()
                .le(SecureInvokeRecord::getNextRetryTime, now)
                .and(wrapper -> wrapper
                        .eq(SecureInvokeRecord::getStatus, SecureInvokeRecord.STATUS_WAIT)
                        .or()
                        .eq(SecureInvokeRecord::getStatus, SecureInvokeRecord.STATUS_RUNNING))
                .orderByAsc(SecureInvokeRecord::getId)
                .last("LIMIT " + limit));
    }

    /** 原子领取到期任务，并写入本次执行租约。 */
    public boolean claim(Long id, Date now, Date leaseUntil) {
        return secureInvokeRecordMapper.update(null, new LambdaUpdateWrapper<SecureInvokeRecord>()
                .eq(SecureInvokeRecord::getId, id)
                .le(SecureInvokeRecord::getNextRetryTime, now)
                .in(SecureInvokeRecord::getStatus, SecureInvokeRecord.STATUS_WAIT, SecureInvokeRecord.STATUS_RUNNING)
                .set(SecureInvokeRecord::getStatus, SecureInvokeRecord.STATUS_RUNNING)
                .set(SecureInvokeRecord::getNextRetryTime, leaseUntil)) > 0;
    }

    /** 仅删除当前租约持有者成功完成的任务。 */
    public boolean finish(Long id, Date leaseUntil) {
        return secureInvokeRecordMapper.delete(new LambdaQueryWrapper<SecureInvokeRecord>()
                .eq(SecureInvokeRecord::getId, id)
                .eq(SecureInvokeRecord::getStatus, SecureInvokeRecord.STATUS_RUNNING)
                .eq(SecureInvokeRecord::getNextRetryTime, leaseUntil)) > 0;
    }

    /** 仅更新当前租约持有者的失败结果。 */
    public boolean retry(Long id, Date leaseUntil, SecureInvokeRecord update) {
        return secureInvokeRecordMapper.update(null, new LambdaUpdateWrapper<SecureInvokeRecord>()
                .eq(SecureInvokeRecord::getId, id)
                .eq(SecureInvokeRecord::getStatus, SecureInvokeRecord.STATUS_RUNNING)
                .eq(SecureInvokeRecord::getNextRetryTime, leaseUntil)
                .set(SecureInvokeRecord::getStatus, update.getStatus())
                .set(SecureInvokeRecord::getFailReason, update.getFailReason())
                .set(SecureInvokeRecord::getNextRetryTime, update.getNextRetryTime())
                .set(SecureInvokeRecord::getRetryTimes, update.getRetryTimes())) > 0;
    }
}
