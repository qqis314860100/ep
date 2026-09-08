package com.tianshu.assets.ai.application;

/** AI 能力服务访问失败：传输层与契约层失败的唯一出口，供上层按类型降级（转 SSE error / 提示重试）。 */
public class AiCapabilityException extends RuntimeException {

    public enum AiCapabilityError {
        /** 服务密钥无效/被拒绝。 */
        AUTH_FAILED,
        /** 请求超时。 */
        TIMEOUT,
        /** 服务不可用/5xx。 */
        UNAVAILABLE,
        /** 响应不符合契约（非 2xx 之外的解析失败等）。 */
        PROTOCOL
    }

    private final AiCapabilityError error;

    public AiCapabilityException(AiCapabilityError error, String message) {
        super(message);
        this.error = error;
    }

    public AiCapabilityException(AiCapabilityError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public AiCapabilityError error() {
        return error;
    }
}
