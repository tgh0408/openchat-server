package com.openchat.user.service;

import cn.hutool.core.util.RandomUtil;
import com.openchat.chat.service.FileAssistantService;
import com.openchat.chat.service.DisplayNameService;
import com.openchat.chat.service.RoomGroupService;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.utils.AssertUtil;
import com.openchat.common.utils.RequestHolder;
import com.openchat.user.dao.UserDao;
import com.openchat.user.domain.entity.*;
import com.openchat.user.domain.enums.IdTypeEnum;
import com.openchat.user.domain.enums.TerminalEnum;
import com.openchat.user.domain.validate.PhoneOrEmailValidator;
import com.openchat.user.domain.vo.req.*;
import com.openchat.user.domain.vo.resp.GlobalUidResp;
import com.openchat.user.domain.vo.resp.LoginResp;
import com.openchat.user.domain.vo.resp.UserInfoResp;
import com.openchat.common.service.MinioBucketService;
import jakarta.validation.Valid;
import com.openchat.user.service.adapter.NameSortKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.regex.Pattern;

import static com.openchat.user.domain.validate.PhoneOrEmailValidator.PHONE_REGEX;

/** 用户资料、注册及账号操作服务。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    final LoginService loginService;
    final UserDao userDao;
    private final FileAssistantService fileAssistantService;
    private final RoomGroupService roomGroupService;
    private final DisplayNameService displayNames;
    private final RegistrationAvatarService registrationAvatars;
    private final IpService ipService;
    private final MinioBucketService minioBucketService;
    private static final String baseChars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*";

    public UserInfoResp getUserInfo(Long uid) {
        return toUserInfoResp(userDao.get(IdTypeEnum.ID, uid));
    }

    public GlobalUidResp getGlobalUid(Long uid) {
        User user = userDao.get(IdTypeEnum.ID, uid, "用户不存在");
        return GlobalUidResp.builder()
                .uid(user.getId())
                .clientSqlPassword(user.getClientSqlPassword())
                .build();
    }

    public UserInfoResp getUserInfo(TargetInfoReq targetInfoReq, Long viewerUid) {
        //通过正则判断请求的类型
        if (targetInfoReq.getCode() == null) {
            check_req_type(targetInfoReq);
        }
        User user = userDao.get(targetInfoReq.getCode(),targetInfoReq.getSearchText(), "用户不存在");
        return displayNames.fillProfile(toUserInfoResp(user), viewerUid, targetInfoReq.getRoomId());
    }

    //通过正则校验类型,邮箱还是手机还是用户id
    private TargetInfoReq check_req_type(TargetInfoReq targetInfoReq) {
        if (Pattern.matches(PHONE_REGEX, targetInfoReq.getSearchText())) {
            targetInfoReq.setCode(IdTypeEnum.PHONE);
        } else if (Pattern.matches(PhoneOrEmailValidator.EMAIL_REGEX, targetInfoReq.getSearchText())) {
            targetInfoReq.setCode(IdTypeEnum.EMAIL);
        } else if (Pattern.matches(PhoneOrEmailValidator.NUMBER_REGEX, targetInfoReq.getSearchText())) {
            targetInfoReq.setCode(IdTypeEnum.ID);
        } else {
            targetInfoReq.setCode(IdTypeEnum.UNKNOWN);
        }
        return targetInfoReq;
    }

    public void modifyInfo(Long uid, ChangeInfoReq changeInfoReq) {
        userDao.modifyInfo(uid, changeInfoReq);
    }

    public Map<Long, User> getBatch(List<Long> atUidList) {
        return userDao.getBatch(atUidList);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createUser(RegisterReq loginReq) {
        String userId = loginReq.getUserId().trim();
        User user = new User();
        switch (PhoneOrEmailValidator.getIdType(userId)){
            case PHONE -> {
                user.setPhone(Long.valueOf(userId));
                AssertUtil.isNull(userDao.get(IdTypeEnum.PHONE,userId), "当前手机号已被注册,请更换手机号");
            }
            case EMAIL -> {
                user.setEmail(userId);
                AssertUtil.isNull(userDao.get(IdTypeEnum.EMAIL,userId), "当前邮箱已被注册,请更换手机号");
            }
            default -> throw new BusinessException("账号格式不正确");
        }
        user.setPassword(loginReq.getPassword());
        user.setName(loginReq.getUsername());
        user.setNameSortKey(NameSortKey.build(user.getName()));
        // 先保存原始 IP，归属地在事务提交后异步补齐，不等待第三方网络。
        user.refreshIp(RequestHolder.getIp());
        if (loginReq.getAvatar() != null) {
            String avatar = registrationAvatars.upload(loginReq.getAvatar());
            user.setAvatar(avatar);
        }
        String sqlitePassword = RandomUtil.randomString(baseChars, 16);
        user.setClientSqlPassword(sqlitePassword);
        try {
            if (!userDao.save(user)) {
                throw new BusinessException("用户创建失败");
            }
        } catch (DuplicateKeyException exception) {
            // 唯一索引是集群并发下的最终约束，不能只依赖上面的预检查。
            throw new BusinessException("账号或用户名已存在");
        }
        // 注册成功返回前已具有真实房间号，客户端从会话列表直接打开文件助手。
        fileAssistantService.getOrCreate(user.getId());
        roomGroupService.joinAllUserGroup(user.getId());
        Long uid = user.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    ipService.refreshIpDetailAsync(uid);
                } catch (RuntimeException exception) {
                    // 用户已提交，附加信息投递失败不能把注册响应变成失败。
                    log.warn("Failed to schedule registration IP details, uid={}", uid, exception);
                }
            }
        });
    }

    public LoginResp login(LoginReq loginReq, String deviceId, TerminalEnum terminal) {
        TargetInfoReq targetInfoReq = check_req_type(TargetInfoReq.builder().searchText(loginReq.getUserId()).build());
        User one = userDao.findByCredentials(
                targetInfoReq.getCode(), loginReq.getUserId(), loginReq.getPassword());
        if (one == null) {
            throw new BusinessException("用户名或密码错误");
        }
        // 只有凭据校验成功的显式登录才能替换会话，快捷进入和 SSE 重连只校验。
        String token = loginService.login(one.getId(), deviceId, terminal);
        return LoginResp.builder()
                .uid(one.getId())
                .token(token)
                .clientSqlPassword(one.getClientSqlPassword())
                .build();
    }

    /** 仅转换返回资料的头像地址，不修改共享缓存中的用户对象。 */
    private UserInfoResp toUserInfoResp(User user) {
        UserInfoResp response = new UserInfoResp(user);
        response.setAvatar(minioBucketService.toAvatarUrl(response.getAvatar()));
        return response;
    }

    public void loginOut(Long uid, TerminalEnum terminal, String token) {
        loginService.logout(uid, terminal, token);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long uid, @Valid ChangePasswordReq changePasswordReq) {
        User user = userDao.get(IdTypeEnum.ID, uid);
        if (!user.getPassword().equals(changePasswordReq.getOldPassword())) {
            throw new BusinessException("旧密码验证失败");
        }
        if (!changePasswordReq.getNewPassword().equals(changePasswordReq.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }
        User update = new User();
        update.setId(uid);
        update.setPassword(changePasswordReq.getNewPassword());
        userDao.updateById(update);
    }

}
