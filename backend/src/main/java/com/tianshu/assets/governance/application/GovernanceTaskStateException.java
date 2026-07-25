package com.tianshu.assets.governance.application;

public class GovernanceTaskStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    public static final String LEGACY_READ_ONLY_MESSAGE = "历史进度任务为只读，请按问题池重新建单";

    public GovernanceTaskStateException(String message) {
        super(message);
    }
}
