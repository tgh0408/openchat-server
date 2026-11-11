package com.openchat.user.service;


import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.openchat.chat.dao.MessageDao;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.service.RoomAccessService;
import com.openchat.common.algorithm.encryption.SHA_256;
import com.openchat.common.annotation.RedissonLock;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.utils.duplicateFiltering.StrTreeMap;
import com.openchat.oss.OssProperties;
import com.openchat.oss.OssUrlUtil;
import com.openchat.user.dao.EmojiDao;
import com.openchat.user.domain.dto.EmojiInfoDTO;
import com.openchat.user.domain.dto.FileInfoDTO;
import com.openchat.user.domain.entity.Emoji;
import com.openchat.user.domain.entity.UserEmoji;
import com.openchat.common.domain.enums.OssSceneEnum;
import com.openchat.common.service.MinioBucketService;
import com.openchat.user.domain.vo.oss.UploadFileReq;
import com.openchat.user.domain.vo.req.UserEmojiReq;
import com.openchat.user.dao.UserEmojiDao;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 用户表情包服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserEmojiService {
    final UserEmojiDao userEmojiDao;
    final OssService ossService;
    final OssProperties ossProperties;
    final MinioBucketService minioBucketService;
    final EmojiDao emojiDao;
    final MessageDao messageDao;
    final RoomAccessService roomAccessService;
    final StrTreeMap strTreeMap;


    public List<EmojiInfoDTO> list(Long uid) {
        return userEmojiDao.listDetailsByUid(uid).stream()
                .filter(Objects::nonNull)
                .peek(item -> item.setUrl(OssUrlUtil.toPublicUrl(
                        ossProperties.getPublicEndpoint(),
                        minioBucketService.currentBucket(),
                        item.getUrl())))
                .toList();
    }

    /**
     * 新增表情包
     *
     * @param uid 用户ID
     **/
    @RedissonLock(key = "#uid")
    @Transactional(rollbackFor = Exception.class)
    public EmojiInfoDTO insert(UserEmojiReq req, Long uid, MultipartFile file) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        byte[] streamBytes;
        String fileName;
        if (req.getMsgId() != null || req.getEmojiId() != null) {
            FileInfoDTO fileInfo = saveOnMessage(req, uid);
            if (fileInfo == null) return describe(emojiDao.get(req.getEmojiId()), uid);
            streamBytes = fileInfo.getStreamBytes();
            fileName = fileInfo.getFileName();
        } else if (file == null) {
            throw new BusinessException("上传表情包无效的参数");
        } else {
            streamBytes = IOUtils.toByteArray(file.getInputStream());
            fileName = file.getOriginalFilename();
        }
        //对文件进行sha-256加密计算
        String hashFile = SHA_256.hashFile(new ByteArrayInputStream(streamBytes));
        boolean has = strTreeMap.hasDuplicate(hashFile);
        if (has) {
            //表情包在库中已存在则判断用户是否存有此表情包
            return describe(saveToUser(uid, hashFile), uid);
        } else {
            return describe(saveEmoji(uid, fileName, new ByteArrayInputStream(streamBytes), hashFile), uid);
        }
    }

    /** 返回服务端确认的表情元数据，供客户端立即更新收藏缓存。 */
    private EmojiInfoDTO describe(Emoji emoji, Long uid) {
        if (emoji == null) throw new BusinessException("表情不存在");
        UserEmoji collection = userEmojiDao.getCollection(uid, emoji.getId());
        if (collection == null || collection.getDeleteStatus() != YesOrNoEnum.NO) {
            throw new BusinessException("表情收藏不存在");
        }
        EmojiInfoDTO result = new EmojiInfoDTO();
        result.setImgId(emoji.getId());
        result.setUrl(OssUrlUtil.toPublicUrl(
                ossProperties.getPublicEndpoint(), minioBucketService.currentBucket(), emoji.getUrl()));
        result.setSha256(emoji.getSha256());
        result.setSort(collection.getSort());
        return result;
    }

    private FileInfoDTO saveOnMessage(UserEmojiReq req, Long uid) {
        //检查消息类型是图片还是表情包
        Room room = roomAccessService.requireCurrentMember(uid, req.getRoomId());
        RoomTypeEnum roomTypeEnum = room.getType();
        Message message = messageDao.getById(roomTypeEnum, req.getMsgId());
        if (message == null || message.getStatus() != YesOrNoEnum.NO) {
            throw new BusinessException("找不到此消息");
        }
        if (!Objects.equals(message.getRoomId(), room.getId())) {
            throw new BusinessException("只能收藏当前会话中的消息");
        }
        if (message.getType() == MessageTypeEnum.EMOJI) {
            Long actualEmojiId = message.getExtra() == null || message.getExtra().getEmojisMsgDTO() == null
                    ? null : message.getExtra().getEmojisMsgDTO().getEmojiId();
            if (actualEmojiId == null || !Objects.equals(actualEmojiId, req.getEmojiId())
                    || emojiDao.get(actualEmojiId) == null) {
                throw new BusinessException("表情与消息不匹配或已不存在");
            }
            collect(uid, actualEmojiId);
            return null;
        } else if (message.getType() == MessageTypeEnum.IMG) {
            //图片则拉取并且判断是否存在表情包中
            FileInfoDTO infoDTO = new FileInfoDTO();
            infoDTO.setFileName(message.getExtra().getFileMsg().getFileName());
            infoDTO.setStreamBytes(ossService.getObject(message.getContent()));
            return infoDTO;
        } else {
            throw new BusinessException("无效的消息状态");
        }
    }

    private Emoji saveToUser(Long uid, String hashFile) {
        Long imgId = (Long) strTreeMap.getFilterMap().get(hashFile);
        Emoji stored = emojiDao.get(imgId);
        if (stored == null || !Objects.equals(stored.getSha256(), hashFile)) {
            throw new BusinessException("表情数据已变化，请重试");
        }
        collect(uid, imgId);
        return stored;
    }

    private Emoji saveEmoji(Long uid, String fileName, InputStream stream, String hashFile) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        try (stream) {
            UUID uuid = UUID.fastUUID();
            String suffix = FileNameUtil.getSuffix(fileName);
            String yearAndMonth = DateUtil.format(new Date(), DatePattern.NORM_MONTH_PATTERN);
            String path = StrUtil.SLASH + OssSceneEnum.EMOJI.getPath() + StrUtil.SLASH + yearAndMonth + StrUtil.SLASH + uuid + StrUtil.DOT + suffix;
            path = ossService.uploadFile(new UploadFileReq(null, path, stream, OssSceneEnum.EMOJI));
            //将文件存入本地
            Emoji emoji = new Emoji();
            emoji.setUrl(path);
            emoji.setCreateId(uid);
            emoji.setSha256(hashFile);
            if (!emojiDao.save(emoji)) throw new BusinessException("保存表情失败");
            collect(uid, emoji.getId());
            //过滤器加入此文件
            strTreeMap.addWordMap(hashFile, emoji.getId());
            return emoji;
        }
    }

    public void remove(Long id, Long uid) {
        requireEmojiId(id);
        if (userEmojiDao.deleteCollection(uid, id) != 1)
            throw new BusinessException("表情收藏不存在或已经删除");
    }

    /** 移动本人收藏到最前，返回本次事务确认的完整列表。 */
    @RedissonLock(key = "#uid")
    @Transactional(rollbackFor = Exception.class)
    public List<EmojiInfoDTO> moveToFront(Long id, Long uid) {
        requireEmojiId(id);
        UserEmoji target = userEmojiDao.getCollection(uid, id);
        if (target == null || target.getDeleteStatus() != YesOrNoEnum.NO)
            throw new BusinessException("只能移动本人已收藏的表情");
        if (emojiDao.get(id) == null) throw new BusinessException("表情不存在");
        UserEmoji first = userEmojiDao.getFirstCollection(uid);
        if (first == null) throw new BusinessException("表情不存在");
        if (!Objects.equals(first.getEmojiId(), id)) {
            long sort = allocateSort(uid);
            if (userEmojiDao.moveToFront(uid, id, target.getSort(), sort) != 1)
                throw new BusinessException("表情收藏已变化，请刷新后重试");
        }
        return list(uid);
    }

    /** 在账号级分布式锁内按索引读取最小排序值，避免并发分配重复排序号。 */
    private long allocateSort(Long uid) {
        Long minimum = userEmojiDao.getMinimumSort(uid);
        if (minimum == null) return 0L;
        if (minimum == Long.MIN_VALUE) throw new BusinessException("表情排序值已达下限");
        return minimum - 1;
    }

    /** 新增及逻辑删除后的恢复均排到最前，已收藏项不会因重复请求改变顺序。 */
    private void collect(Long uid, Long emojiId) {
        UserEmoji existing = userEmojiDao.getCollection(uid, emojiId);
        if (existing != null && existing.getDeleteStatus() == YesOrNoEnum.NO)
            throw new BusinessException("这个表情包已经收藏过了");
        UserEmoji row = new UserEmoji();
        row.setUid(uid);
        row.setEmojiId(emojiId);
        row.setDeleteStatus(YesOrNoEnum.NO);
        row.setSort(allocateSort(uid));
        if (userEmojiDao.saveCollection(row) <= 0)
            throw new BusinessException("保存表情收藏失败");
    }

    private void requireEmojiId(Long id) {
        if (id == null || id <= 0) throw new BusinessException("表情包标识无效");
    }
}
