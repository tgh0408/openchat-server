package com.openchat.user.controller;

import com.openchat.common.annotation.FrequencyControl;
import com.openchat.common.domain.dto.RequestInfo;
import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.domain.vo.resp.CheckCodeResp;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.service.CaptchaService;
import com.openchat.common.utils.MailUtils;
import com.openchat.common.utils.RequestHolder;
import com.openchat.user.domain.vo.req.*;
import com.openchat.user.domain.vo.resp.GlobalUidResp;
import com.openchat.user.domain.vo.resp.LoginResp;
import com.openchat.user.domain.vo.resp.UserInfoResp;
import com.openchat.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;

import jakarta.validation.Valid;
import java.util.List;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author <a  >TGH</a>
 * @since 2023-11-05
 */
@Slf4j
@RestController
@RequestMapping("/capi/user")
@Tag(name = "用户相关接口")
@RequiredArgsConstructor
public class UserController {
    final UserService userService;

    @PostMapping("/public/login")
    @Operation(summary = "用户账号密码登录")
    @FrequencyControl(time = 6, count = 3, target = FrequencyControl.Target.IP)
    public ApiResult<LoginResp> login(@Valid @RequestBody LoginReq loginReq){
        //验证码校验
//        CaptchaService.checkCaptcha(loginReq.getCodeKey(), loginReq.getCheckCode());
        RequestInfo requestInfo = RequestHolder.get();
        return ApiResult.success(userService.login(loginReq, requestInfo.getDeviceId(), requestInfo.getTerminal()));
    }

    @Operation(summary = "获取验证码")
    @GetMapping(value = "/public/getCaptcha")
    public ApiResult<CheckCodeResp> getCaptcha(){
        log.info("IP :{} 请求验证码",RequestHolder.get().getIp());
        return ApiResult.success(CaptchaService.getAndSetCaptcha());
    }

    @PostMapping(value = "/loginOut")
    @Operation(summary = "用户退出登录")
    public ApiResult<Void> loginOut(){
        RequestInfo requestInfo = RequestHolder.get();
        userService.loginOut(requestInfo.getUid(), requestInfo.getTerminal(), requestInfo.getToken());
        return ApiResult.success();
    }

    @GetMapping("/globalUid")
    @Operation(summary = "尝试根据token解析uid并且返回(快捷登录)")
    public ApiResult<GlobalUidResp> getuid(){
        return ApiResult.success(userService.getGlobalUid(RequestHolder.get().getUid()));
    }

    /** 注册表单只绑定公开字段，头像与账号资料在同一次请求中提交。 */
    @InitBinder("registrationForm")
    public void bindRegistration(WebDataBinder binder) {
        binder.setAllowedFields("userId", "username", "password", "avatar");
    }

    @PostMapping(value = "/public/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "用户账号密码注册（可选头像）")
    public ApiResult<Void> registerForm(
            @Valid @ModelAttribute("registrationForm") RegisterReq req, BindingResult errors) {
        if (errors.hasErrors()) {
            // 不记录绑定异常全文，避免把密码和文件内容写入日志。
            throw new BusinessException("注册信息无效，请检查账号、昵称和密码长度");
        }
        userService.createUser(req);
        return ApiResult.success();
    }

    @GetMapping("/userinfo")
    @Operation(summary = "获取本人用户信息")
    public ApiResult<UserInfoResp> getUserInfo(){
        RequestInfo requestInfo = RequestHolder.get();
        return ApiResult.success(userService.getUserInfo(requestInfo.getUid()));
    }

    @PostMapping("/target/info")
    @Operation(summary = "根据对方的uid获得头像等信息")
    public ApiResult<UserInfoResp> getTargetInfo(@Valid @RequestBody TargetInfoReq req){
        //手动校验格式
        return ApiResult.success(userService.getUserInfo(req, RequestHolder.get().getUid()));
    }
    
    @PostMapping ("/changeUserInfo")
    @Operation(summary = "修改用户信息")                 //如果需要spring校验入参就要加入@Valid
    public ApiResult<UserInfoResp> modifyName(@Valid @RequestBody ChangeInfoReq changeInfoReq){
        userService.modifyInfo(RequestHolder.get().getUid(),changeInfoReq);
        return ApiResult.success();
    }

    @PostMapping ("/changePassword")
    @Operation(summary = "修改密码")
    public ApiResult<UserInfoResp> changePassword(@Valid @RequestBody ChangePasswordReq changePasswordReq){
        userService.changePassword(RequestHolder.get().getUid(),changePasswordReq);
        return ApiResult.success();
    }

    @GetMapping("/mail")
    @Operation(summary = "发邮件")
    public ApiResult<Void> sendMail(){
        MailUtils.sendMail("1003340666@qq.com","测试邮件","测试邮件");
        return ApiResult.success();
    }

}

