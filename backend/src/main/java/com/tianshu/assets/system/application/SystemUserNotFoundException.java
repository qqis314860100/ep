package com.tianshu.assets.system.application;

public class SystemUserNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SystemUserNotFoundException(long id) {
        super("用户不存在或不可访问：" + id);
    }
}
