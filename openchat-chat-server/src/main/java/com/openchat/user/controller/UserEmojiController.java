package com.openchat.user.controller;


import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.utils.RequestHolder;
import com.openchat.user.domain.dto.EmojiInfoDTO;
import com.openchat.user.domain.vo.req.IdReqVO;
import com.openchat.user.domain.vo.req.UserEmojiReq;
import com.openchat.user.service.UserEmojiService;
import io.minio.errors.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * <p>
 * 用户表情包 前端控制器
 * </p>
 *
 * @author TGH
 * @since 2024-01-03
 */
@RestController
@RequestMapping("/capi/user/emoji")
@Tag(name = "用户表情包管理相关接口")
public class UserEmojiController {

    /**
     * 用户表情包 Service
     */
    @Resource
    private UserEmojiService emojiService;


    /**
     * 表情包列表
     * @return 表情包列表
     **/
    @GetMapping("/list")
    @Operation(summary = "表情包列表")
    public ApiResult<List<EmojiInfoDTO>> getEmojisPage() {
        return ApiResult.success(emojiService.list(RequestHolder.get().getUid()));
    }


    /**
     * 新增表情包
     *
     * @param req 用户表情包
     * @return 表情包
     **/
    @PostMapping
    @Operation(summary = "新增表情包")
    public ApiResult<EmojiInfoDTO> insertEmojis(@RequestParam(value = "file",required = false) MultipartFile file, UserEmojiReq req) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return ApiResult.success(emojiService.insert(req, RequestHolder.get().getUid(),file));
    }
    /**
     * 新增表情包
     * @param req 用户表情包存在id的情况
     * @return 表情包
     **/
    @PostMapping(consumes = "application/json")
    public ApiResult<EmojiInfoDTO> handleFormUpload(@Valid @RequestBody UserEmojiReq req) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return ApiResult.success(emojiService.insert(req, RequestHolder.get().getUid(),null));
    }

    /**
     * 删除表情包
     *
     * @return 删除结果
     **/
    @PostMapping("/delete")
    @Operation(summary = "删除表情包")
    public ApiResult<Void> deleteEmojis(@Valid @RequestBody IdReqVO reqVO) {
        emojiService.remove(reqVO.getImgId(), RequestHolder.get().getUid());
        return ApiResult.success();
    }

    @PostMapping("/front")
    @Operation(summary = "将收藏表情移到最前")
    public ApiResult<List<EmojiInfoDTO>> moveEmojiToFront(@Valid @RequestBody IdReqVO reqVO) {
        return ApiResult.success(emojiService.moveToFront(reqVO.getImgId(), RequestHolder.get().getUid()));
    }
}

