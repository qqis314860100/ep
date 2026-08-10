package com.tianshu.assets.governance.api;

import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.governance.standard.application.GovernanceDataStandardService;
import com.tianshu.assets.governance.standard.domain.GovernanceDataStandard;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardImpactReview;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardRule;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance/standards")
public class GovernanceDataStandardController {

    private final GovernanceDataStandardService service;

    public GovernanceDataStandardController(GovernanceDataStandardService service) {
        this.service = service;
    }

    @GetMapping
    public List<GovernanceDataStandard> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public GovernanceDataStandard get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GovernanceDataStandard create(@RequestBody CreateStandardRequest request) {
        return service.create(request.toCommand());
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public GovernanceDataStandard createVersion(
            @PathVariable long id, @RequestBody CreateVersionRequest request) {
        return service.createVersion(id, request.toCommand());
    }

    @PostMapping("/{id}/enable")
    public GovernanceDataStandardService.ActivationResult enable(
            @PathVariable long id, @RequestBody VersionRequest request) {
        return service.enable(id, request.version());
    }

    @PostMapping("/{id}/disable")
    public GovernanceDataStandard disable(
            @PathVariable long id, @RequestBody VersionRequest request) {
        return service.disable(id, request.version());
    }

    @GetMapping("/{id}/impact-reviews")
    public List<GovernanceStandardImpactReview> impactReviews(@PathVariable long id) {
        return service.impactReviews(id);
    }

    public record CreateStandardRequest(
            String standardCode, long standardVersion, String name,
            List<AssetType> applicableAssetTypes, String ownerUserId, String ownerName,
            String changeSummary, List<GovernanceStandardRule> rules) {
        GovernanceDataStandardService.CreateCommand toCommand() {
            return new GovernanceDataStandardService.CreateCommand(
                    standardCode, standardVersion, name, applicableAssetTypes, ownerUserId, ownerName,
                    changeSummary, rules);
        }
    }

    public record CreateVersionRequest(
            long standardVersion, String name, List<AssetType> applicableAssetTypes,
            String ownerUserId, String ownerName, String changeSummary,
            List<GovernanceStandardRule> rules) {
        GovernanceDataStandardService.CreateVersionCommand toCommand() {
            return new GovernanceDataStandardService.CreateVersionCommand(
                    standardVersion, name, applicableAssetTypes, ownerUserId, ownerName, changeSummary, rules);
        }
    }

    public record VersionRequest(long version) {}
}
