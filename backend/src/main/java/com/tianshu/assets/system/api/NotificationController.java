package com.tianshu.assets.system.api;

import com.tianshu.assets.system.application.NotificationService;
import com.tianshu.assets.system.application.NotificationService.NotificationView;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知中心：聚合资料待办、本人到期/逾期任务、管理员逾期升级与扫描失败提醒。
 * 身份来自会话（SessionIdentityFilter 覆写 X-User-Id/X-User-Roles）；无会话匿名请求按 demo 兼容处理。
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    @Autowired
    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationView notifications(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesCsv) {
        return service.notifications(userId, parseRoles(rolesCsv));
    }

    private static Set<String> parseRoles(String rolesCsv) {
        if (rolesCsv == null || rolesCsv.isBlank()) return Set.of();
        var roles = new HashSet<String>();
        for (var part : rolesCsv.split(",")) {
            var role = part.trim();
            if (!role.isEmpty()) roles.add(role);
        }
        return roles;
    }
}
