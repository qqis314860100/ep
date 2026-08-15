package com.tianshu.assets.system.api;

import com.tianshu.assets.system.application.NotificationService;
import com.tianshu.assets.system.application.NotificationService.NotificationView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 通知中心：聚合待办、到期任务与扫描失败提醒。 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    @Autowired
    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationView notifications() {
        return service.notifications();
    }
}
