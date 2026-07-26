package com.tianshu.assets.governance.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;

abstract class JdbcGovernanceSupport {
    protected final JdbcClient jdbc;
    protected final ObjectMapper json;
    private final boolean writable;

    JdbcGovernanceSupport(
            JdbcClient jdbc,
            ObjectMapper json,
            @Value("${asset.database-writes-enabled:false}") boolean writable) {
        this.jdbc = jdbc;
        this.json = json;
        this.writable = writable;
    }

    protected void requireWritable() {
        if (!writable) throw new GovernanceConflictException("当前数据库配置为只读，禁止执行治理写入");
    }

    protected String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("治理数据序列化失败", exception);
        }
    }

    protected <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("治理数据反序列化失败", exception);
        }
    }

    protected void requireUpdated(int count, Supplier<? extends RuntimeException> failure) {
        if (count != 1) throw failure.get();
    }
}
