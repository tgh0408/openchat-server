package com.openchat.user.domain.entity;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

@Data
public class IpInfo implements Serializable {
    //注册时候的ip
    private String createIp;
    //注册时的ip详情
    private IpDetail createIpDetail;
    //最新登录的ip
    private String updateIp;
    //最新登录的ip详情
    private IpDetail updateIpDetail;
    // 北京时间自然日（yyyy-MM-dd），由数据库与登录 IP 一起原子更新。
    private String lastLoginIpDate;

    public void refreshIp(String ip) {
        if (StringUtils.isEmpty(ip)) {
            return;
        }
        updateIp = ip;
        if (createIp == null) {
            createIp = ip;
        }
    }

    public String needRefreshIp() {
        boolean need = Optional.ofNullable(updateIpDetail)
                .map(IpDetail::getIp)
                .filter(ip -> ip.equals(updateIp))
                .isPresent();
        return need ? null : updateIp;
    }

    public void refreshIpDetail(IpDetail ipDetail) {
        if (Objects.equals(createIp,ipDetail.getIp())){
            this.createIpDetail = ipDetail;
        }
        if (Objects.equals(updateIp,ipDetail.getIp())){
            this.updateIpDetail = ipDetail;
        }
    }
}
