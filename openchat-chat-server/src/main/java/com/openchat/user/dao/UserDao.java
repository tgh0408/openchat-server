package com.openchat.user.dao;

import com.openchat.common.exception.BusinessException;
import com.openchat.common.service.LockService;
import com.openchat.common.utils.AssertUtil;
import com.openchat.user.domain.entity.User;
import com.openchat.user.domain.entity.IpDetail;
import com.openchat.user.domain.enums.IdTypeEnum;
import com.openchat.user.domain.vo.req.ChangeInfoReq;
import com.openchat.user.mapper.UserMapper;
import com.openchat.user.service.adapter.NameSortKey;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.openchat.common.service.cache.LockedRedisCache;
import com.openchat.common.utils.JsonUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author <a  >TGH</a>
 * @since 2023-11-05
 */
@Repository
public class UserDao {
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;

    /** 用户资料变更只携带缓存键，不持有可变实体。 */
    public record UserChanged(Long uid, Set<Account> affectedAccounts) {
        public UserChanged {
            Objects.requireNonNull(uid, "用户 ID 不能为空");
            affectedAccounts = Set.copyOf(affectedAccounts);
        }

        public UserChanged(Long uid) {
            this(uid, Set.of());
        }
    }

    public record Account(IdTypeEnum typeEnum, String value){}

    private final LockedRedisCache<Long, User> cache;
    private final LockedRedisCache<Account, Long> typeUserMappingCache;

    public UserDao(UserMapper userMapper, ApplicationEventPublisher events, LockService locks) {
        this.userMapper = userMapper;
        this.events = events;
        this.cache = LockedRedisCache.create(locks,
                uid -> "openchat:user:snapshot:{" + uid + "}",
                this::loadUserCache, payload -> JsonUtils.toObj(payload, User.class));
        this.typeUserMappingCache = LockedRedisCache.create(locks,
                key -> "openchat:user:index:" + key.typeEnum().name() + ":{" + key.value() + "}",
                this::loadMappingCache, payload -> JsonUtils.toObj(payload, Long.class));
    }

    /** 加载缺失的 UID 用户快照。 */
    private Map<Long, User> loadUserCache(List<Long> uids) {
        return userMapper.selectByIds(uids).stream().collect(Collectors.toMap(User::getId, user -> user));
    }

    /** 加载缺失的登录标识与用户 ID 映射。 */
    private Map<Account, Long> loadMappingCache(List<Account> accounts) {
        Map<Account, Long> result = new HashMap<>();
        for (Account account : accounts) {
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .select(User::getId)
                    .eq(getField(account.typeEnum()), account.value()));
            if (user != null) {
                result.put(account, user.getId());
            }
        }
        return result;
    }

    /** 登录凭据在同一条查询中匹配，不向业务层暴露 Wrapper。 */
    public User findByCredentials(IdTypeEnum accountType, String account, String password) {
        SFunction<User, ?> field = getField(accountType);
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(field, account).eq(User::getPassword, password));
    }

    private static @NonNull SFunction<User, ?> getField(IdTypeEnum accountType) {
        return switch (accountType) {
            case EMAIL -> User::getEmail;
            case PHONE -> User::getPhone;
            case ID -> User::getId;
            case UNKNOWN -> throw new IllegalArgumentException("无法解析账号类型: " + accountType);
        };
    }

    public User get(IdTypeEnum type, String value) {
        Objects.requireNonNull(value, "账号不能为空");
        if (value.isBlank()) throw new IllegalArgumentException("账号不能为空");
        return switch (type) {
            case ID -> cache.get(Long.valueOf(value));
            case EMAIL, PHONE -> {
                Long uid = typeUserMappingCache.get(new Account(type, value));
                yield uid == null ? null : cache.get(uid);
            }
            case UNKNOWN -> throw new IllegalArgumentException("无法解析账号类型: " + type);
        };
    }

    public User get(IdTypeEnum type, Long value) {
        return get(type, String.valueOf(value));
    }

    public User get(IdTypeEnum type, String value, String error) {
        User user = get(type, value);
        AssertUtil.isNonNull(user, error);
        return user;
    }

    public User get(IdTypeEnum type, Long value, String error) {
        User user = get(type, value);
        AssertUtil.isNonNull(user, error);
        return user;
    }

    public Map<Long, User> getBatch(List<Long> uids) {
        return cache.getBatch(uids);
    }

    /** 提交后失效共享快照；无事务写入立即失效，回滚不处理。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void cleanCache(UserChanged event) {
        RuntimeException failure = null;
        try {
            cache.deleteCache(event.uid());
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            typeUserMappingCache.deleteBatchCache(List.copyOf(event.affectedAccounts()));
        } catch (RuntimeException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    public boolean save(User user) {
        boolean saved = userMapper.insert(user) > 0;
        if (saved) {
            Set<Account> accounts = new LinkedHashSet<>();
            addAccount(accounts, IdTypeEnum.EMAIL, user.getEmail());
            addAccount(accounts, IdTypeEnum.PHONE, user.getPhone());
            events.publishEvent(new UserChanged(user.getId(), accounts));
        }
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(User user) {
        User previous = previousAccounts(user.getId(), user.getEmail(), user.getPhone());
        LambdaUpdateWrapper<User> update = accountUpdate(
                user.getId(), previous, user.getEmail(), user.getPhone());
        if (user.getName() != null) {
            update.set(User::getNameSortKey, NameSortKey.build(user.getName()));
        }
        boolean updated = userMapper.update(user, update) > 0;
        checkAccountConflict(previous, updated);
        if (updated) events.publishEvent(new UserChanged(user.getId(),
                changedAccounts(previous, user.getEmail(), user.getPhone())));
        return updated;
    }

    /**
     * 仅补齐仍匹配该 IP 的详情；并发登录、重复任务不能回写旧 IP 或覆盖已填详情。
     */
    @Transactional(rollbackFor = Exception.class)
    public void fillIpDetail(Long uid, IpDetail detail) {
        String json = JsonUtils.toStr(detail);
        int updated = userMapper.fillCreateIpDetail(uid, detail.getIp(), json);
        updated += userMapper.fillUpdateIpDetail(uid, detail.getIp(), json);
        if (updated > 0) events.publishEvent(new UserChanged(uid));
    }

    /** 原子领取当天的登录 IP 更新机会，保留注册信息；仅获胜者失效缓存。 */
    public boolean updateConnectionIp(Long uid, String ip) {
        int updated = userMapper.updateConnectionIp(uid, ip);
        if (updated > 0) events.publishEvent(new UserChanged(uid));
        return updated > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void modifyInfo(Long uid, ChangeInfoReq changeInfoReq) {
        Long phone = changeInfoReq.getPhone() == null ? null : Long.valueOf(changeInfoReq.getPhone());
        User previous = previousAccounts(uid, changeInfoReq.getEmail(), phone);
        boolean updated = userMapper.update(null, accountUpdate(
                        uid, previous, changeInfoReq.getEmail(), phone)
                .set(changeInfoReq.getName()!=null,User::getName, changeInfoReq.getName())
                .set(changeInfoReq.getName()!=null, User::getNameSortKey,
                        NameSortKey.build(changeInfoReq.getName()))
                .set(changeInfoReq.getAvatar()!=null,User::getAvatar, changeInfoReq.getAvatar())
                .set(changeInfoReq.getSex()!=null,User::getSex,changeInfoReq.getSex())
                .set(changeInfoReq.getEmail()!=null,User::getEmail, changeInfoReq.getEmail())
                .set(phone!=null,User::getPhone, phone)
                .set(changeInfoReq.getAddFunction()!=null,User::getAddFunction, changeInfoReq.getAddFunction())
                .set(changeInfoReq.getPersonalitySignature()!=null,User::getPersonalitySignature, changeInfoReq.getPersonalitySignature())) > 0;
        checkAccountConflict(previous, updated);
        if (updated) events.publishEvent(new UserChanged(uid,
                changedAccounts(previous, changeInfoReq.getEmail(), phone)));
    }

    /** 只在修改账号标识时读取旧值；事务内仍走缓存入口，由缓存组件负责回源。 */
    private User previousAccounts(Long uid, String email, Long phone) {
        if (email == null && phone == null) return null;
        return cache.get(uid, "用户不存在");
    }

    /** 带旧联系方式条件更新，防止并发变更后使用不匹配的旧键集合失效缓存。 */
    private LambdaUpdateWrapper<User> accountUpdate(Long uid, User previous, String email, Long phone) {
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>().eq(User::getId, uid);
        if (previous != null && email != null) {
            if (previous.getEmail() == null) update.isNull(User::getEmail);
            else update.eq(User::getEmail, previous.getEmail());
        }
        if (previous != null && phone != null) {
            if (previous.getPhone() == null) update.isNull(User::getPhone);
            else update.eq(User::getPhone, previous.getPhone());
        }
        return update;
    }

    private void checkAccountConflict(User previous, boolean updated) {
        if (previous != null && !updated) {
            throw new BusinessException("用户联系方式已变化，请刷新后重试");
        }
    }

    private Set<Account> changedAccounts(User previous, String email, Long phone) {
        Set<Account> accounts = new LinkedHashSet<>();
        if (previous == null) return accounts;
        if (email != null && !Objects.equals(previous.getEmail(), email)) {
            addAccount(accounts, IdTypeEnum.EMAIL, previous.getEmail());
            addAccount(accounts, IdTypeEnum.EMAIL, email);
        }
        if (phone != null && !Objects.equals(previous.getPhone(), phone)) {
            addAccount(accounts, IdTypeEnum.PHONE, previous.getPhone());
            addAccount(accounts, IdTypeEnum.PHONE, phone);
        }
        return accounts;
    }

    private void addAccount(Set<Account> accounts, IdTypeEnum type, Object value) {
        if (value != null && !value.toString().isBlank()) {
            accounts.add(new Account(type, value.toString()));
        }
    }
}
