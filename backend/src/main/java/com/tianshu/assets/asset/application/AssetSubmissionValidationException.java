package com.tianshu.assets.asset.application;

public class AssetSubmissionValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssetSubmissionValidationException() {
        super("提交前需要补充资料编号、名称、功能说明、专业类别、完整适用范围和文件");
    }
}
