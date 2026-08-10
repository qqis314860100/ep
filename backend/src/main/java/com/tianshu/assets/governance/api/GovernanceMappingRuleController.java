package com.tianshu.assets.governance.api;

import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.governance.mapping.application.GovernanceMappingRuleService;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingRule;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/governance/mappings")
public class GovernanceMappingRuleController {
    private final GovernanceMappingRuleService service;
    public GovernanceMappingRuleController(GovernanceMappingRuleService service) { this.service = service; }

    @GetMapping public List<GovernanceMappingRule> list(
            @RequestParam(required = false) GovernanceMappingStatus status,
            @RequestParam(required = false) String sourceDimension,
            @RequestParam(required = false) String query) {
        return service.list(status, sourceDimension, query);
    }
    @GetMapping("/{id}") public GovernanceMappingRule get(@PathVariable long id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public GovernanceMappingRule create(@RequestBody CreateRequest request) {
        return service.create(request.toCommand());
    }
    @PostMapping("/{id}/versions") @ResponseStatus(HttpStatus.CREATED) public GovernanceMappingRule version(@PathVariable long id, @RequestBody VersionRequest request) {
        return service.createVersion(id, request.toCommand());
    }
    @PostMapping("/{id}/confirm") public GovernanceMappingRule confirm(@PathVariable long id, @RequestBody ConfirmRequest request) {
        return service.confirm(id, new GovernanceMappingRuleService.ConfirmCommand(request.version(), request.userId(), request.userName(), request.comment()));
    }
    @PostMapping("/{id}/disable") public GovernanceMappingRule disable(@PathVariable long id, @RequestBody VersionRequest request) {
        return service.disable(id, request.version());
    }
    @GetMapping("/{id}/history") public List<GovernanceMappingRule> history(@PathVariable long id) {
        var current = service.get(id);
        return service.list(null, current.sourceDimension(), null).stream().filter(item -> item.standardId() == current.standardId() && item.standardVersion() == current.standardVersion() && item.sourceValue().equals(current.sourceValue()) && item.scope().equals(current.scope())).toList();
    }

    public record CreateRequest(long standardId, String sourceDimension, String sourceValue, String targetDictionaryCategory, long targetDictionaryItemId, AssetScope scope, boolean ambiguous, long affectedAssetCount) {
        GovernanceMappingRuleService.CreateCommand toCommand() { return new GovernanceMappingRuleService.CreateCommand(standardId, sourceDimension, sourceValue, targetDictionaryCategory, targetDictionaryItemId, scope, ambiguous, affectedAssetCount); }
    }
    public record VersionRequest(long standardId, long standardVersion, String sourceDimension, String sourceValue, String targetDictionaryCategory, long targetDictionaryItemId, AssetScope scope, boolean ambiguous, long affectedAssetCount, long version) {
        GovernanceMappingRuleService.VersionCommand toCommand() { return new GovernanceMappingRuleService.VersionCommand(standardId, standardVersion, sourceDimension, sourceValue, targetDictionaryCategory, targetDictionaryItemId, scope, ambiguous, affectedAssetCount); }
    }
    public record ConfirmRequest(long version, String userId, String userName, String comment) {}
}
