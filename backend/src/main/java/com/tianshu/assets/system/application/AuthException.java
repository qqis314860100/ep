package com.tianshu.assets.system.application;

/** 登录失败 / 会话未认证（HTTP 401）。 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
