package com.openchat.transaction.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.openchat.transaction.dao.SecureInvokeRecordDao;
import com.openchat.transaction.domain.dto.SecureInvokeDTO;
import com.openchat.transaction.domain.entity.SecureInvokeRecord;
import com.openchat.transaction.utils.JsonUtils;
import tools.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Description: 安全执行处理器
 * Author: 
 * Date: 2023-08-20
 */
@Slf4j
@AllArgsConstructor
public class SecureInvokeService {

    public static final double RETRY_INTERVAL_MINUTES = 2D;
    private static final int RETRY_BATCH_SIZE = 50;
    private static final int CLAIM_LEASE_MINUTES = 5;

    private final SecureInvokeRecordDao secureInvokeRecordDao;

    private final Executor executor;
    private final PlatformTransactionManager transactions;

    /**
     * 统一调度所有到期的可靠调用记录。
     *
     * 任务在数据库中持久化后才能跨进程、跨节点恢复。调度器每次只读取有限批次，并通过条件更新领取任务；
     * 节点中断后，执行租约到期的任务可以被其他节点重新领取。完成和失败更新都会校验租约，避免旧节点
     * 恢复后覆盖新节点的执行结果。具体业务由可靠调用记录中的类名、方法名和参数快照决定，事务模块不依赖业务模块。
     */
    public void retry() {
        List<SecureInvokeRecord> secureInvokeRecords = secureInvokeRecordDao.getDueRecords(RETRY_BATCH_SIZE);
        Date now = new Date();
        for (SecureInvokeRecord secureInvokeRecord : secureInvokeRecords) {
            Date leaseUntil = DateUtil.offsetMinute(now, CLAIM_LEASE_MINUTES);
            if (secureInvokeRecordDao.claim(secureInvokeRecord.getId(), now, leaseUntil)) {
                secureInvokeRecord.setStatus(SecureInvokeRecord.STATUS_RUNNING);
                secureInvokeRecord.setNextRetryTime(leaseUntil);
                doAsyncInvoke(secureInvokeRecord);
            }
        }
    }

    public void save(SecureInvokeRecord record) {
        if (!secureInvokeRecordDao.save(record) || record.getId() == null) {
            throw new IllegalStateException("Failed to persist reliable invocation record");
        }
    }

    private void retryRecord(SecureInvokeRecord record, String errorMsg) {
        Integer retryTimes = record.getRetryTimes() + 1;
        SecureInvokeRecord update = new SecureInvokeRecord();
        update.setFailReason(errorMsg);
        update.setNextRetryTime(getNextRetryTime(retryTimes));
        update.setRetryTimes(retryTimes);
        if (retryTimes > record.getMaxRetryTimes()) {
            update.setStatus(SecureInvokeRecord.STATUS_FAIL);
        } else {
            update.setStatus(SecureInvokeRecord.STATUS_WAIT);
        }
        boolean updated = secureInvokeRecordDao.retry(record.getId(), record.getNextRetryTime(), update);
        if (!updated) {
            log.warn("Skip stale secure invoke retry result, recordId={}", record.getId());
        }
    }

    private Date getNextRetryTime(Integer retryTimes) {//或者可以采用退避算法
        double waitMinutes = Math.pow(RETRY_INTERVAL_MINUTES, retryTimes);//重试时间指数上升 2m 4m 8m 16m
        return DateUtil.offsetMinute(new Date(), (int) waitMinutes);
    }

    private void removeRecord(SecureInvokeRecord record) {
        boolean finished = secureInvokeRecordDao.finish(record.getId(), record.getNextRetryTime());
        if (!finished) {
            log.warn("Skip stale secure invoke success result, recordId={}", record.getId());
        }
    }

    public void invoke(SecureInvokeRecord record, boolean async) {
        boolean inTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        // 在事务中表明是在存数据
        if (!inTransaction) {
            return;
        }
        record.setStatus(SecureInvokeRecord.STATUS_RUNNING);
        record.setNextRetryTime(DateUtil.offsetMinute(new Date(), CLAIM_LEASE_MINUTES));
        this.save(record);
        //保存后等待事务结束调用
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            @SneakyThrows
            public void afterCommit() {
                //事务后执行 (并非表示事务已经结束,仅代表数据已入本地消息库)
                if (async) {
                    doAsyncInvoke(record);
                } else {
                    doInvoke(record);
                }
            }
        });
    }

    public void doAsyncInvoke(SecureInvokeRecord record) {
        executor.execute(() -> {
            log.info("Thread name:{} ", Thread.currentThread().getName());
            doInvoke(record);
        });
    }

    /** 回放已提交的任务时挂起旧事务，不能让业务方法加入 afterCommit 的残留连接。 */
    public void doInvoke(SecureInvokeRecord record) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        template.executeWithoutResult(status -> invokeCommittedRecord(record));
    }

    //根据数据库中的数据通过反射调用方法
    private void invokeCommittedRecord(SecureInvokeRecord record) {
        SecureInvokeDTO secureInvokeDTO = record.getSecureInvokeDTO();
        boolean previouslyInvoking = SecureInvokeHolder.isInvoking();
        try {
            SecureInvokeHolder.setInvoking();
            Class<?> beanClass = Class.forName(secureInvokeDTO.getClassName());
            Object bean = SpringUtil.getBean(beanClass);
            List<String> parameterStrings = JsonUtils.toList(secureInvokeDTO.getParameterTypes(), String.class);
            List<Class<?>> parameterClasses = getParameters(parameterStrings);
            Method method = ReflectUtil.getMethod(beanClass, secureInvokeDTO.getMethodName(), parameterClasses.toArray(new Class[]{}));
            Object[] args = getArgs(secureInvokeDTO, parameterClasses);
            //执行方法
            method.invoke(bean, args);
            //执行成功更新状态
            removeRecord(record);
        } catch (Throwable e) {
            log.error("SecureInvokeService invoke fail", e);
            //执行失败，等待下次执行
            retryRecord(record, e.getMessage());
        } finally {
            SecureInvokeHolder.invoked();
            if (previouslyInvoking) {
                SecureInvokeHolder.setInvoking();
            }
        }
    }

    @NotNull
    private Object[] getArgs(SecureInvokeDTO secureInvokeDTO, List<Class<?>> parameterClasses) {
        JsonNode jsonNode = JsonUtils.toJsonNode(secureInvokeDTO.getArgs());
        Object[] args = new Object[jsonNode.size()];
        for (int i = 0; i < jsonNode.size(); i++) {
            Class<?> aClass = parameterClasses.get(i);
            args[i] = JsonUtils.nodeToValue(jsonNode.get(i), aClass);
        }
        return args;
    }

    @NotNull
    private List<Class<?>> getParameters(List<String> parameterStrings) {
        return parameterStrings.stream().map(name -> {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                log.error("SecureInvokeService class not fund", e);
            }
            return null;
        }).collect(Collectors.toList());
    }
}
