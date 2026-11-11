package com.openchat.common.event.listener;

import com.openchat.common.event.UserOnlineEvent;
import com.openchat.user.dao.UserDao;
import com.openchat.user.domain.entity.User;
import com.openchat.user.domain.enums.UserActiveStatusEnum;
import com.openchat.user.service.IpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class UserOnlineListener {
    final IpService ipService;
    final UserDao userDao;

//    @Async //异步执行
    @TransactionalEventListener(classes = UserOnlineEvent.class,fallbackExecution = true) //默认在事务提交后执行,如果没有事务的时候是否执行
    public void send(UserOnlineEvent event){
        // 迟到事件只更新上线字段，不能回写旧资料或覆盖登录 IP 的每日领取标记。
        User user = new User();
        user.setId(event.getUser().getId());
        user.setActiveStatus(UserActiveStatusEnum.ON_LINE);
        user.setLastOptTime(new Date());//更新最新的上线时间
        userDao.updateById(user);
        //更新ip详情
        ipService.refreshIpDetailAsync(user.getId());
    }
}
