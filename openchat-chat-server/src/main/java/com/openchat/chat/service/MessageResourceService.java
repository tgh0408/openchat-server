package com.openchat.chat.service;

import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.user.dao.UserDao;
import com.openchat.common.service.MinioBucketService;
import com.openchat.user.domain.enums.IdTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** OSS object ownership and message attachment path validation. */
@Service
@RequiredArgsConstructor
public class MessageResourceService {
    private final UserDao userDao;
    private final MinioBucketService minioBucketService;

    public static boolean isAttachment(MessageTypeEnum type) {
        return type == MessageTypeEnum.IMG
                || type == MessageTypeEnum.VIDEO
                || type == MessageTypeEnum.FILE
                || type == MessageTypeEnum.SOUND;
    }

    public void requireOwner(Long uid) {
        if (uid == null || userDao.get(IdTypeEnum.ID,uid) == null) {
            throw new BusinessException("Upload owner does not exist");
        }
    }

    public List<String> paths(Message message) {
        if (message == null || (!isAttachment(message.getType())
                && message.getType() != MessageTypeEnum.TEMP)) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        if (isAttachment(message.getType())) paths.add(message.getContent());
        if (message.getType() == MessageTypeEnum.VIDEO
                && message.getExtra() != null && message.getExtra().getVideoMsgDTO() != null) {
            paths.add(message.getExtra().getVideoMsgDTO().getThumbUrl());
        }
        if (message.getExtra() != null && message.getExtra().getUpload() != null) {
            var upload = message.getExtra().getUpload();
            if (upload.getObjectPath() != null) paths.add(upload.getObjectPath());
            if (upload.getPosterPaths() != null) paths.addAll(upload.getPosterPaths());
        }
        List<String> normalized = paths.stream().filter(Objects::nonNull).distinct().toList();
        for (String path : normalized) objectName(message.getFromUid(), path);
        return normalized;
    }

    public String objectName(Long uid, String path) {
        String prefix = minioBucketService.prefix();
        String pattern = Pattern.quote(prefix) + "(img|video|file|sound)/\\d{4}-\\d{2}/" + uid
                + "/[a-fA-F0-9-]{32,36}\\.(?i:png|jpe?g|gif|bmp|mp4|mkv|avi|mov|wmv|flv|webm|mpg|mpeg|bin|wav)";
        if (uid == null || uid <= 0 || path == null || !path.matches(pattern)) {
            throw new BusinessException("Invalid attachment object ownership");
        }
        return path.substring(prefix.length());
    }
}
