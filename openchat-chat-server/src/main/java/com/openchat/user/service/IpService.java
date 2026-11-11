package com.openchat.user.service;

import cn.hutool.http.HttpUtil;
import com.openchat.common.config.ThreadPoolConfig;
import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.utils.JsonUtils;
import com.openchat.user.dao.UserDao;
import com.openchat.user.domain.entity.IpDetail;
import com.openchat.user.domain.entity.IpInfo;
import com.openchat.user.domain.entity.User;
import com.openchat.user.domain.enums.IdTypeEnum;
import tools.jackson.core.type.TypeReference;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
@Slf4j
public class IpService {
    final UserDao userDao;
    private static final int IP_LOOKUP_TIMEOUT_MILLIS = 2000;
    private final Executor executor;

    public IpService(UserDao userDao, @Qualifier(ThreadPoolConfig.IP_DETAIL_EXECUTOR) Executor executor) {
        this.userDao = userDao;
        this.executor = executor;
    }

    /** 登录时按北京时间自然日最多更新一次 IP，事务提交后异步补齐归属地。 */
    @Transactional(rollbackFor = Exception.class)
    public void refreshIp(Long uid, String ip) {
        if (uid == null || StringUtil.isBlank(ip)) {
            return;
        }
        // 日期领取与 IP 更新同一事务；只有跨节点竞争获胜的登录才调度详情。
        if (!userDao.updateConnectionIp(uid, ip)) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    refreshIpDetailAsync(uid);
                } catch (RuntimeException exception) {
                    log.warn("Failed to schedule SSE IP details, uid={}", uid, exception);
                }
            }
        });
    }

    /** 有界查询归属地，失败时返回 null，不伪造详情。 */
    public IpDetail getIpDetail(String ip) {
        if (StringUtil.isBlank(ip)) {
            return null;
        }
        return getIpDetailOrNull(ip);
    }

    public void refreshIpDetailAsync(Long uid) {
        try {
            executor.execute(() -> {
                try {
                    User user = userDao.get(IdTypeEnum.ID,uid);
                    if (user == null || user.getIpInfo() == null) {
                        return;
                    }
                    IpInfo ipInfo = user.getIpInfo();
                    // 注册后可能已从另一 IP 登录，注册与最新 IP 的缺失详情分别补齐。
                    String createIp = ipInfo.getCreateIp();
                    if (!StringUtil.isBlank(createIp)
                            && (ipInfo.getCreateIpDetail() == null
                            || !Objects.equals(createIp, ipInfo.getCreateIpDetail().getIp()))) {
                        refreshDetail(uid, createIp);
                    }
                    String updateIp = ipInfo.needRefreshIp();
                    if (!StringUtil.isBlank(updateIp) && !Objects.equals(createIp, updateIp)) {
                        refreshDetail(uid, updateIp);
                    } else if (!StringUtil.isBlank(updateIp) && ipInfo.getCreateIpDetail() != null
                            && Objects.equals(createIp, ipInfo.getCreateIpDetail().getIp())) {
                        // 同 IP 已有注册详情时直接复用，不再请求第三方。
                        userDao.fillIpDetail(uid, ipInfo.getCreateIpDetail());
                    }
                } catch (RuntimeException exception) {
                    log.warn("Failed to refresh IP details, uid={}", uid, exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            log.warn("IP detail queue unavailable, uid={}", uid, exception);
        }
    }

    private void refreshDetail(Long uid, String ip) {
        IpDetail detail = tryGetIpDetailOrNullTreeTimes(ip);
        if (detail != null && Objects.equals(ip, detail.getIp())) {
            userDao.fillIpDetail(uid, detail);
        }
    }

    public IpDetail tryGetIpDetailOrNullTreeTimes(String ip) {
        for (int i = 0;; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            IpDetail ipDetail = getIpDetail(ip);
            if (Objects.nonNull(ipDetail)){
                return ipDetail;
            }
            if (i == 2) {
                break;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        log.warn("IP detail lookup exhausted retries");
        return null;
    }

    private static IpDetail getIpDetailOrNull(String ip) {
        try {
            String url = "https://ip.taobao.com/outGetIpInfo?ip="+ip+"&accessKey=alibaba-inc";
            String resp = HttpUtil.get(url, IP_LOOKUP_TIMEOUT_MILLIS);
            ApiResult<IpDetail> result = JsonUtils.toObj(resp, new TypeReference<ApiResult<IpDetail>>(){});
            if (result == null || result.getData() == null
                    || !Objects.equals(ip, result.getData().getIp())) {
                return null;
            }
            return result.getData();
        }catch (Exception e){
            return null;
        }
    }

}
