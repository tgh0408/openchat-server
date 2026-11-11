package com.openchat.user.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.openchat.chat.service.RoomAccessService;
import com.openchat.chat.domain.dto.PushMessageDTO;
import com.openchat.common.constant.MQConstant;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.event.FriendApplyChangedEvent;
import com.openchat.common.event.listener.UserApplyListener;
import com.openchat.common.exception.BusinessException;
import com.openchat.user.dao.UserApplyDao;
import com.openchat.user.domain.dto.ApplyChat;
import com.openchat.user.domain.entity.UserApply;
import com.openchat.user.domain.enums.ApplyReadEnum;
import com.openchat.user.domain.enums.ApplyStatusEnum;
import com.openchat.user.domain.enums.ApplyTypeEnum;
import com.openchat.user.domain.vo.req.FriendApplyReq;
import com.openchat.user.domain.vo.req.FriendReplyReq;
import com.openchat.user.mapper.UserApplyMapper;
import com.openchat.user.service.adapter.FriendAdapter;
import com.openchat.transaction.service.MQProducer;
import com.openchat.user.domain.enums.SSEPushTypeEnum;
import com.openchat.user.domain.vo.resp.FriendApplyNotification;
import com.openchat.user.domain.vo.resp.FriendUnRead;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 不启动 Spring 或数据库，覆盖原申请权限、通知及消息追加条件。 */
public class FriendApplicationReplyTest {
    private UserApplyDao dao;
    private RoomAccessService access;
    private ApplicationEventPublisher publisher;
    private FriendService service;
    private UserApply apply;

    @Before
    public void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "reply-test"), UserApply.class);
        dao = mock(UserApplyDao.class);
        access = mock(RoomAccessService.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new FriendService(publisher, null, null, dao, null, null,
                null, null, null, null, access, null, null);
        apply = new UserApply();
        apply.setId(9L);
        apply.setUid(1L);
        apply.setTargetId(2L);
        apply.setType(ApplyTypeEnum.ADD_FRIEND);
        apply.setStatus(ApplyStatusEnum.WAIT_APPROVE);
        apply.setReadStatus(ApplyReadEnum.READ);
        apply.setSenderReadStatus(ApplyReadEnum.READ);
        apply.setSenderDelete(YesOrNoEnum.NO);
        apply.setReceiverDeletedStatus(YesOrNoEnum.NO);
        apply.setMsg(List.of(new ApplyChat(1L, "hello")));
        apply.setVersion(7);
        when(dao.getParticipantPending(1L, 9L)).thenReturn(apply);
        when(dao.getParticipantPending(2L, 9L)).thenReturn(apply);
        when(dao.appendPendingMessage(eq(apply), any(ApplyChat.class), eq(true))).thenReturn(true);
        when(dao.appendPendingMessage(eq(apply), any(ApplyChat.class), eq(false))).thenReturn(true);
    }

    @Test
    public void recipientAppendsAsSelfAndNotifiesBothPartiesWithoutApproval() {
        service.replyApply(2L, new FriendReplyReq(9L, "reply"));
        verify(dao).getParticipantPending(2L, 9L);
        verify(dao).appendPendingMessage(apply, new ApplyChat(2L, "reply"), false);
        verify(publisher).publishEvent(argThat((ApplicationEvent event) ->
                event instanceof FriendApplyChangedEvent changed && changed.getUid().equals(1L)));
        verify(publisher).publishEvent(argThat((ApplicationEvent event) ->
                event instanceof FriendApplyChangedEvent changed && changed.getUid().equals(2L)));
        verifyNoMoreInteractions(dao);
        assertEquals(ApplyStatusEnum.WAIT_APPROVE, apply.getStatus());
        assertEquals(ApplyReadEnum.READ, apply.getReadStatus());
    }

    @Test
    public void senderAppendsAsSelfAndNotifiesPeerAndSelfWithoutApproval() {
        service.replyApply(1L, new FriendReplyReq(9L, "answer"));
        verify(dao).getParticipantPending(1L, 9L);
        verify(dao).appendPendingMessage(apply, new ApplyChat(1L, "answer"), true);
        verify(access).isMyFriendBlacklisted(1L, 2L);
        verify(access).isMyFriendBlacklisted(2L, 1L);
        verify(publisher).publishEvent(argThat((ApplicationEvent event) ->
                event instanceof FriendApplyChangedEvent changed && changed.getUid().equals(1L)));
        verify(publisher).publishEvent(argThat((ApplicationEvent event) ->
                event instanceof FriendApplyChangedEvent changed && changed.getUid().equals(2L)));
        verifyNoMoreInteractions(dao, publisher, access);
        assertEquals(ApplyStatusEnum.WAIT_APPROVE, apply.getStatus());
        assertEquals(ApplyReadEnum.READ, apply.getSenderReadStatus());
    }

    @Test
    public void outsiderOrUnknownApplicationCannotReply() {
        assertThrows(BusinessException.class, () -> service.replyApply(3L, new FriendReplyReq(9L, "reply")));
        assertThrows(BusinessException.class, () -> service.replyApply(1L, new FriendReplyReq(10L, "reply")));
        assertThrows(BusinessException.class, () -> service.replyApply(2L, new FriendReplyReq(10L, "reply")));
        verify(dao, never()).appendPendingMessage(any(), any(), anyBoolean());
        verifyNoInteractions(publisher, access);
    }

    @Test
    public void BlacklistInEitherDirectionPreventsReply() {
        when(access.isMyFriendBlacklisted(1L, 2L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.replyApply(2L, new FriendReplyReq(9L, "reply")));
        verify(dao, never()).appendPendingMessage(any(), any(), anyBoolean());
        verifyNoInteractions(publisher);
        when(access.isMyFriendBlacklisted(1L, 2L)).thenReturn(false);
        when(access.isMyFriendBlacklisted(2L, 1L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.replyApply(2L, new FriendReplyReq(9L, "reply")));
    }

    @Test
    public void senderCannotReplyWhenEitherPartyBlacklistedTheOther() {
        when(access.isMyFriendBlacklisted(2L, 1L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.replyApply(1L, new FriendReplyReq(9L, "reply")));
        when(access.isMyFriendBlacklisted(2L, 1L)).thenReturn(false);
        when(access.isMyFriendBlacklisted(1L, 2L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.replyApply(1L, new FriendReplyReq(9L, "reply")));
        verify(dao, never()).appendPendingMessage(any(), any(), anyBoolean());
        verifyNoInteractions(publisher);
    }

    @Test
    public void concurrentAppendOrApprovalConflictFailsWithoutRetryOrNotification() {
        when(dao.appendPendingMessage(eq(apply), any(), eq(false))).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.replyApply(2L, new FriendReplyReq(9L, "reply")));
        verify(dao, times(1)).appendPendingMessage(eq(apply), any(), eq(false));
        verifyNoInteractions(publisher);
    }

    @Test
    public void senderConflictFailsWithoutRetryOrNotification() {
        when(dao.appendPendingMessage(eq(apply), any(), eq(true))).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.replyApply(1L, new FriendReplyReq(9L, "reply")));
        verify(dao, times(1)).appendPendingMessage(eq(apply), any(), eq(true));
        verifyNoInteractions(publisher);
    }

    @Test
    public void notificationFailurePropagatesToTransactionalCaller() {
        doThrow(new IllegalStateException("reliable call failed")).when(publisher).publishEvent(any(ApplicationEvent.class));
        assertThrows(IllegalStateException.class, () -> service.replyApply(2L, new FriendReplyReq(9L, "reply")));
        // Mock 验证异常传播；真实事务回滚和 MQ 恢复需数据库集成验证。
    }

    @Test
    public void daoAppendPreservesSnapshotAndUsesVersionAndPendingConditions() {
        UserApplyMapper mapper = mock(UserApplyMapper.class);
        UserApplyDao realDao = new UserApplyDao(mapper);
        AtomicInteger calls = new AtomicInteger();
        when(mapper.update(any(UserApply.class), any(Wrapper.class))).thenAnswer(invocation -> {
            UserApply updated = invocation.getArgument(0);
            LambdaUpdateWrapper<UserApply> condition = invocation.getArgument(1);
            int call = calls.getAndIncrement();
            assertEquals(List.of(new ApplyChat(1L, "hello"),
                    new ApplyChat(call == 1 ? 1L : 2L, "reply")), updated.getMsg());
            assertEquals(call == 1 ? ApplyReadEnum.UNREAD : null, updated.getReadStatus());
            assertEquals(call == 1 ? null : ApplyReadEnum.UNREAD, updated.getSenderReadStatus());
            assertNotNull(updated.getUpdateTime());
            assertVersionedPatch(updated, condition, 7);
            String sql = condition.getSqlSegment();
            assertFalse(sql.contains("JSON_LENGTH"));
            assertTrue(sql.contains("status ="));
            assertTrue(sql.contains("target_id ="));
            assertTrue(condition.getParamNameValuePairs().containsValue(ApplyStatusEnum.WAIT_APPROVE));
            return call == 2 ? 0 : 1;
        });
        assertTrue(realDao.appendPendingMessage(apply, new ApplyChat(2L, "reply"), false));
        assertTrue(realDao.appendPendingMessage(apply, new ApplyChat(1L, "reply"), true));
        assertFalse(realDao.appendPendingMessage(apply, new ApplyChat(2L, "reply"), false));
        assertEquals(1, apply.getMsg().size());
        assertEquals(ApplyReadEnum.READ, apply.getReadStatus());
        assertEquals(ApplyReadEnum.READ, apply.getSenderReadStatus());
        assertEquals(Integer.valueOf(7), apply.getVersion());
    }

    @Test
    public void daoSelectsExactPendingParticipantAndScopesDeletionToEachDirection() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "reply-select-test"), UserApply.class);
        UserApplyMapper mapper = mock(UserApplyMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserApply> condition = invocation.getArgument(0);
            String sql = condition.getSqlSegment();
            for (String column : List.of("id =", "target_id =", "uid =", "type =", "status =",
                    "receiver_deleted =", "sender_delete ="))
                assertTrue(sql.contains(column));
            assertTrue(sql.matches(".*status = .* AND \\(\\(target_id = .* AND receiver_deleted = .*\\)"
                    + " OR \\(uid = .* AND sender_delete = .*\\)\\).*"));
            assertTrue(condition.getParamNameValuePairs().containsValue(9L));
            assertEquals(2L, condition.getParamNameValuePairs().values().stream()
                    .filter(value -> Long.valueOf(1L).equals(value) || Long.valueOf(2L).equals(value)).count());
            assertTrue(condition.getParamNameValuePairs().containsValue(ApplyTypeEnum.ADD_FRIEND));
            assertTrue(condition.getParamNameValuePairs().containsValue(ApplyStatusEnum.WAIT_APPROVE));
            assertEquals(2L, condition.getParamNameValuePairs().values().stream()
                    .filter(YesOrNoEnum.NO::equals).count());
            return apply;
        });
        UserApplyDao realDao = new UserApplyDao(mapper);
        assertSame(apply, realDao.getParticipantPending(1L, 9L));
        assertSame(apply, realDao.getParticipantPending(2L, 9L));
        verify(mapper, times(2)).selectOne(any(Wrapper.class));
    }

    @Test
    public void daoApprovalAndRefusalUseVersionWithoutWritingOtherSnapshotFields() {
        UserApplyMapper mapper = mock(UserApplyMapper.class);
        UserApplyDao realDao = new UserApplyDao(mapper);
        AtomicInteger calls = new AtomicInteger();
        when(mapper.update(any(UserApply.class), any(Wrapper.class))).thenAnswer(invocation -> {
            UserApply updated = invocation.getArgument(0);
            LambdaUpdateWrapper<UserApply> condition = invocation.getArgument(1);
            int call = calls.getAndIncrement();
            assertEquals(call == 0 ? ApplyStatusEnum.APPROVED : ApplyStatusEnum.REJECTED, updated.getStatus());
            assertNull(updated.getMsg());
            assertNull(updated.getReadStatus());
            assertVersionedPatch(updated, condition, 7);
            for (String column : List.of("id =", "uid =", "target_id =", "type =", "status ="))
                assertTrue(condition.getSqlSegment().contains(column));
            assertTrue(condition.getParamNameValuePairs().containsValue(ApplyStatusEnum.WAIT_APPROVE));
            return call == 0 ? 1 : 0;
        });
        assertTrue(realDao.agree(apply));
        assertFalse(realDao.refuseApply(apply));
        assertEquals(ApplyStatusEnum.WAIT_APPROVE, apply.getStatus());
        assertEquals(Integer.valueOf(7), apply.getVersion());
        verify(mapper, times(2)).update(any(UserApply.class), any(Wrapper.class));
    }

    @Test
    public void daoReadUsesObservedVersionAndThrowsOnConflictWithoutRetry() {
        UserApplyMapper mapper = mock(UserApplyMapper.class);
        UserApplyDao realDao = new UserApplyDao(mapper);
        apply.setReadStatus(ApplyReadEnum.UNREAD);
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserApply> condition = invocation.getArgument(0);
            for (String column : List.of("id =", "target_id =", "type =", "read_status ="))
                assertTrue(condition.getSqlSegment().contains(column));
            assertTrue(condition.getParamNameValuePairs().containsValue(9L));
            assertTrue(condition.getParamNameValuePairs().containsValue(2L));
            assertTrue(condition.getParamNameValuePairs().containsValue(ApplyReadEnum.UNREAD));
            return apply;
        });
        AtomicInteger calls = new AtomicInteger();
        when(mapper.update(any(UserApply.class), any(Wrapper.class))).thenAnswer(invocation -> {
            UserApply updated = invocation.getArgument(0);
            LambdaUpdateWrapper<UserApply> condition = invocation.getArgument(1);
            assertEquals(ApplyReadEnum.READ, updated.getReadStatus());
            assertNull(updated.getSenderReadStatus());
            assertNull(updated.getMsg());
            assertNull(updated.getStatus());
            assertVersionedPatch(updated, condition, 7);
            assertTrue(condition.getParamNameValuePairs().containsValue(ApplyReadEnum.UNREAD));
            return calls.getAndIncrement() == 0 ? 1 : 0;
        });
        assertTrue(realDao.readApply(2L, 9L));
        assertThrows(BusinessException.class, () -> realDao.readApply(2L, 9L));
        assertEquals(ApplyReadEnum.UNREAD, apply.getReadStatus());
        assertEquals(Integer.valueOf(7), apply.getVersion());
        verify(mapper, times(2)).selectOne(any(Wrapper.class));
        verify(mapper, times(2)).update(any(UserApply.class), any(Wrapper.class));
    }

    @Test
    public void daoReadMissingOrAlreadyReadIsNoOp() {
        UserApplyMapper mapper = mock(UserApplyMapper.class);
        assertFalse(new UserApplyDao(mapper).readApply(2L, 9L));
        verify(mapper).selectOne(any(Wrapper.class));
        verify(mapper, never()).update(any(UserApply.class), any(Wrapper.class));
    }

    @Test
    public void daoBlacklistRejectsEachPendingApplicationWithItsOwnVersion() {
        UserApplyMapper mapper = mock(UserApplyMapper.class);
        UserApply second = new UserApply();
        second.setId(10L);
        second.setUid(1L);
        second.setTargetId(2L);
        second.setVersion(12);
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserApply> condition = invocation.getArgument(0);
            for (String column : List.of("uid =", "target_id =", "type =", "status ="))
                assertTrue(condition.getSqlSegment().contains(column));
            assertTrue(condition.getParamNameValuePairs().containsValue(1L));
            assertTrue(condition.getParamNameValuePairs().containsValue(2L));
            assertTrue(condition.getParamNameValuePairs().containsValue(ApplyStatusEnum.WAIT_APPROVE));
            return List.of(apply, second);
        });
        AtomicInteger calls = new AtomicInteger();
        when(mapper.update(any(UserApply.class), any(Wrapper.class))).thenAnswer(invocation -> {
            UserApply updated = invocation.getArgument(0);
            LambdaUpdateWrapper<UserApply> condition = invocation.getArgument(1);
            int call = calls.getAndIncrement();
            assertEquals(ApplyStatusEnum.REJECTED, updated.getStatus());
            assertNull(updated.getMsg());
            assertNull(updated.getReadStatus());
            assertVersionedPatch(updated, condition, call == 0 ? 7 : 12);
            assertTrue(condition.getParamNameValuePairs().containsValue(call == 0 ? 9L : 10L));
            return 1;
        });
        assertTrue(new UserApplyDao(mapper).blackUser(2L, 1L));
        verify(mapper).selectList(any(Wrapper.class));
        verify(mapper, times(2)).update(any(UserApply.class), any(Wrapper.class));
    }

    @Test
    public void daoBlacklistConflictThrowsInsteadOfReportingNoPendingApplication() {
        UserApplyMapper mapper = mock(UserApplyMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(apply));
        when(mapper.update(any(UserApply.class), any(Wrapper.class))).thenReturn(0);
        assertThrows(BusinessException.class, () -> new UserApplyDao(mapper).blackUser(2L, 1L));
        verify(mapper).selectList(any(Wrapper.class));
        verify(mapper).update(any(UserApply.class), any(Wrapper.class));
    }

    @Test
    public void daoBlacklistWithoutPendingApplicationsIsNoOp() {
        UserApplyMapper mapper = mock(UserApplyMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertFalse(new UserApplyDao(mapper).blackUser(2L, 1L));
        verify(mapper, never()).update(any(UserApply.class), any(Wrapper.class));
    }

    @Test
    public void daoRejectsMissingVersionInsteadOfBypassingOptimisticLock() {
        UserApplyMapper mapper = mock(UserApplyMapper.class);
        UserApplyDao realDao = new UserApplyDao(mapper);
        apply.setVersion(null);
        assertThrows(BusinessException.class, () -> realDao.appendPendingMessage(apply, new ApplyChat(2L, "reply"), false));
        assertThrows(BusinessException.class, () -> realDao.agree(apply));
        assertThrows(BusinessException.class, () -> realDao.refuseApply(apply));
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(apply);
        assertThrows(BusinessException.class, () -> realDao.readApply(2L, 9L));
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(apply));
        assertThrows(BusinessException.class, () -> realDao.blackUser(2L, 1L));
        verify(mapper, never()).update(any(UserApply.class), any(Wrapper.class));
    }

    @Test
    public void newApplicationStartsAtVersionZeroWithoutReusingPreviousVersion() {
        FriendApplyReq request = new FriendApplyReq();
        request.setTargetUid(2L);
        request.setMessage("again");
        UserApply created = FriendAdapter.buildFriendApply(1L, request, apply);
        assertEquals(Integer.valueOf(0), created.getVersion());
        assertEquals(ApplyReadEnum.UNREAD, created.getReadStatus());
        assertEquals(ApplyReadEnum.READ, created.getSenderReadStatus());
        assertEquals(Integer.valueOf(7), apply.getVersion());
    }

    @Test
    public void senderReadClearsOnlySenderStateAndRejectsConcurrentReply() {
        UserApplyMapper mapper = mock(UserApplyMapper.class);
        UserApplyDao realDao = new UserApplyDao(mapper);
        apply.setSenderReadStatus(ApplyReadEnum.UNREAD);
        apply.setReadStatus(ApplyReadEnum.UNREAD);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(apply);
        AtomicInteger calls = new AtomicInteger();
        when(mapper.update(any(UserApply.class), any(Wrapper.class))).thenAnswer(invocation -> {
            UserApply updated = invocation.getArgument(0);
            LambdaUpdateWrapper<UserApply> condition = invocation.getArgument(1);
            assertEquals(ApplyReadEnum.READ, updated.getSenderReadStatus());
            assertNull(updated.getReadStatus());
            assertNull(updated.getMsg());
            assertNull(updated.getStatus());
            assertVersionedPatch(updated, condition, 7);
            assertTrue(condition.getSqlSegment().contains("sender_read_status ="));
            assertTrue(condition.getParamNameValuePairs().containsValue(ApplyReadEnum.UNREAD));
            return calls.getAndIncrement() == 0 ? 1 : 0;
        });
        assertTrue(realDao.readApply(1L, 9L));
        assertThrows(BusinessException.class, () -> realDao.readApply(1L, 9L));
        assertEquals(ApplyReadEnum.UNREAD, apply.getReadStatus());
        assertEquals(ApplyReadEnum.UNREAD, apply.getSenderReadStatus());
        verify(mapper, times(2)).update(any(UserApply.class), any(Wrapper.class));
    }

    @Test
    public void unreadAndReadQueriesScopeBothDirectionsToCurrentUserAndDeletionFlags() {
        UserApplyMapper mapper = mock(UserApplyMapper.class);
        UserApplyDao realDao = new UserApplyDao(mapper);
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserApply> query = invocation.getArgument(0);
            assertBothUnreadDirections(query);
            assertTrue(query.getSqlSegment().contains("status ="));
            assertTrue(query.getParamNameValuePairs().containsValue(ApplyStatusEnum.WAIT_APPROVE));
            return List.of(apply);
        });
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserApply> query = invocation.getArgument(0);
            assertBothUnreadDirections(query);
            assertTrue(query.getSqlSegment().contains("id ="));
            assertTrue(query.getParamNameValuePairs().containsValue(9L));
            return null;
        });
        assertEquals(List.of(apply), realDao.getUnreadApplies(1L));
        assertFalse(realDao.readApply(1L, 9L));
        verify(mapper, never()).update(any(UserApply.class), any(Wrapper.class));
    }

    @Test
    public void unreadSummaryUsesPeerIdentityForBothDirectionsAndReadNotifiesOnlyReader() {
        when(dao.getUnreadApplies(1L)).thenReturn(List.of(apply));
        when(dao.getUnreadApplies(2L)).thenReturn(List.of(apply));
        assertEquals(List.of(new FriendUnRead(2L, ApplyStatusEnum.WAIT_APPROVE)), service.selectUnreadApply(1L));
        assertEquals(List.of(new FriendUnRead(1L, ApplyStatusEnum.WAIT_APPROVE)), service.selectUnreadApply(2L));
        when(dao.readApply(1L, 9L)).thenReturn(true, false);
        service.readApply(1L, 9L);
        service.readApply(1L, 9L);
        verify(publisher, times(1)).publishEvent(argThat((ApplicationEvent event) ->
                event instanceof FriendApplyChangedEvent changed && changed.getUid().equals(1L)));
        verifyNoMoreInteractions(publisher);
    }

    @Test
    public void replyRegistersReliableSseNotificationsForBothUsersInsideTransaction() {
        MQProducer producer = mock(MQProducer.class);
        UserApplyListener listener = new UserApplyListener(producer);
        doAnswer(invocation -> {
            listener.notifyChanged(invocation.getArgument(0));
            return null;
        }).when(publisher).publishEvent(any(FriendApplyChangedEvent.class));
        for (Long caller : List.of(1L, 2L)) {
            clearInvocations(producer);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                service.replyApply(caller, new FriendReplyReq(9L, "reply"));
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
            for (Long recipient : List.of(1L, 2L)) {
                verify(producer).sendSecureMsg(eq(MQConstant.SSE_TOPIC), argThat(body -> {
                    if (!(body instanceof PushMessageDTO push)) return false;
                    return FriendApplyNotification.EVENT_TYPE.equals(push.getEventType())
                            && push.getPushType() == SSEPushTypeEnum.USER
                            && List.of(recipient).equals(push.getUidList())
                            && recipient.equals(push.getFriendApply().getTargetUid());
                }), eq("friend-apply:" + recipient));
            }
            verifyNoMoreInteractions(producer);
        }
    }

    /** 检查 OR 两侧分别绑定本人、各自未读及删除字段，类型条件不能只约束一侧。 */
    private void assertBothUnreadDirections(LambdaQueryWrapper<UserApply> query) {
        String sql = query.getSqlSegment();
        assertTrue(sql.contains("target_id ="));
        assertTrue(sql.contains("uid ="));
        assertTrue(sql.contains("read_status ="));
        assertTrue(sql.contains("sender_read_status ="));
        assertTrue(sql.contains("receiver_deleted ="));
        assertTrue(sql.contains("sender_delete ="));
        assertTrue(sql.contains(" OR "));
        assertTrue(sql.contains("type ="));
        assertEquals(2L, query.getParamNameValuePairs().values().stream()
                .filter(value -> Long.valueOf(1L).equals(value)).count());
        assertEquals(2L, query.getParamNameValuePairs().values().stream()
                .filter(ApplyReadEnum.UNREAD::equals).count());
        assertEquals(2L, query.getParamNameValuePairs().values().stream()
                .filter(YesOrNoEnum.NO::equals).count());
        assertTrue(query.getParamNameValuePairs().containsValue(ApplyTypeEnum.ADD_FRIEND));
    }

    /** 调用真实插件检查旧版本条件和版本递增，不以 Mock 替代插件行为。 */
    private void assertVersionedPatch(UserApply updated, LambdaUpdateWrapper<UserApply> condition, int originalVersion) {
        assertEquals(Integer.valueOf(originalVersion), updated.getVersion());
        assertFalse(condition.getSqlSegment().contains("version"));
        assertNull(condition.getSqlSet());
        MybatisConfiguration configuration = new MybatisConfiguration();
        MappedStatement statement = new MappedStatement.Builder(configuration,
                UserApplyMapper.class.getName() + ".update",
                new StaticSqlSource(configuration, ""), SqlCommandType.UPDATE).build();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(Constants.ENTITY, updated);
        parameters.put(Constants.WRAPPER, condition);
        new OptimisticLockerInnerInterceptor().beforeUpdate(null, statement, parameters);
        assertEquals(Integer.valueOf(originalVersion + 1), updated.getVersion());
        assertTrue(condition.getSqlSegment().contains("version ="));
        assertTrue(condition.getParamNameValuePairs().containsValue(originalVersion));
    }
}
