package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.responsibility.application.GovernanceResponsibilityService;
import com.tianshu.assets.governance.responsibility.application.GovernanceResponsibilityService.BoardView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组织与责任看板（R4）：员工责任分桶 + 月度治理复盘，供登录员工查看部门治理进度。 */
@RestController
@RequestMapping("/api/v1/governance/responsibility")
public class GovernanceResponsibilityBoardController {

    private final GovernanceResponsibilityService service;
    private final GovernanceAuthorizationService authorization;

    @Autowired
    public GovernanceResponsibilityBoardController(
            GovernanceResponsibilityService service, GovernanceAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping("/board")
    public BoardView board(
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestParam(required = false) String month) {
        authorization.requireAuthenticated(userId);
        return service.board(month);
    }
}
