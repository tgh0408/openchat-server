package com.openchat.system.domain.dto;

/** 管理接口专用身份，不放入聊天用户的 RequestHolder。 */
public record AdminPrincipal(String username, String expiresAt) {
}
