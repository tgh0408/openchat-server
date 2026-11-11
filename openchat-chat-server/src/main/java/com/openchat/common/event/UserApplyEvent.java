package com.openchat.common.event;

import com.openchat.user.domain.entity.UserApply;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
@Getter
public class UserApplyEvent extends ApplicationEvent {
    private final UserApply userApply;
    public UserApplyEvent(Object source, UserApply userApply) {
        super(source);
        this.userApply = userApply;
    }
}
