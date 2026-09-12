package com.tianshu.assets.common.api;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeTest {

    @Test
    void codesAreGloballyUnique() {
        Set<String> seen = new HashSet<>();
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertTrue(seen.add(errorCode.code()),
                    "duplicate error code: " + errorCode.code());
        }
        assertEquals(ErrorCode.values().length, seen.size());
    }

    @Test
    void codesAreLowerSnakeCaseAndCarryStatus() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertTrue(errorCode.code().matches("[a-z][a-z0-9_]*"),
                    "code must be lower snake_case: " + errorCode.code());
            assertNotNull(errorCode.status(), "status missing for " + errorCode.name());
        }
    }
}
