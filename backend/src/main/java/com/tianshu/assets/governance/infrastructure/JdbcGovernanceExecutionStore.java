package com.tianshu.assets.governance.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.execution.domain.*;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile({"local", "oceanbase"})
@ConditionalOnProperty(name="asset.governance-schema-enabled", havingValue="true")
public class JdbcGovernanceExecutionStore extends JdbcGovernanceSupport implements GovernanceExecutionStore {
    public JdbcGovernanceExecutionStore(JdbcClient jdbc, ObjectMapper json,
            @Value("${asset.database-writes-enabled:false}") boolean writable) { super(jdbc, json, writable); }

    @Override public GovernanceItem item(long id) { return jdbc.sql("SELECT payload_json FROM governance_item WHERE id=:id").param("id",id).query(String.class).optional().map(v->decode(v,GovernanceItem.class)).orElseThrow(()->new IllegalArgumentException("治理项不存在")); }
    @Override public List<GovernanceItem> items(long taskId) { return jdbc.sql("SELECT payload_json FROM governance_item WHERE task_id=:id ORDER BY id").param("id",taskId).query(String.class).list().stream().map(v->decode(v,GovernanceItem.class)).toList(); }
    @Override public GovernanceResultVersion currentResult(long itemId) { var id=item(itemId).currentResultVersionId(); return id==null?null:result(id); }
    @Override public List<GovernanceResultVersion> resultsForItem(long itemId) { return jdbc.sql("SELECT payload_json FROM governance_result_version WHERE item_id=:id ORDER BY result_version").param("id",itemId).query(String.class).list().stream().map(v->decode(v,GovernanceResultVersion.class)).toList(); }

    @Override @Transactional public GovernanceResultVersion saveDraft(SaveDraft c) {
        requireWritable(); var item=item(c.itemId()); requireVersion(item,c.expectedItemVersion());
        if(item.assetVersion()!=c.expectedAssetVersion()) throw new GovernanceVersionConflictException("资产版本已变化，请刷新后重试");
        var current=currentResult(item.id());
        if(current!=null && current.status()!=GovernanceResultStatus.DRAFT) throw new GovernanceConflictException("已提交结果不可原地修改");
        GovernanceResultVersion saved;
        if(current==null){
            var key=new GeneratedKeyHolder();
            jdbc.sql("INSERT INTO governance_result_version (item_id,governance_round,result_version,status,version,payload_json) VALUES (:item,:round,1,'DRAFT',0,'{}')")
                    .param("item",item.id()).param("round",item.governanceRound()).update(key,"id");
            saved=new GovernanceResultVersion(key.getKeyAs(Long.class),item.id(),item.governanceRound(),1,c.field(),c.originalValueJson(),c.proposedValueJson(),c.standardVersion(),c.dictionaryVersions(),GovernanceResultStatus.DRAFT,"",c.actorUserId(),c.savedAt(),null,0);
            jdbc.sql("UPDATE governance_result_version SET payload_json=:payload WHERE id=:id").param("payload",encode(saved)).param("id",saved.id()).update();
        } else {
            saved=new GovernanceResultVersion(current.id(),current.itemId(),current.governanceRound(),current.resultVersion(),current.field(),current.originalValueJson(),c.proposedValueJson(),current.standardVersion(),current.dictionaryVersions(),GovernanceResultStatus.DRAFT,current.reworkReason(),c.actorUserId(),c.savedAt(),null,current.version()+1);
            updateResult(saved,current.version());
        }
        updateItem(copy(item,GovernanceItemStatus.PROCESSING,item.version()+1,saved.id(),item.blockReason(),item.governanceRound(),item.reworkSourceItemId()),item.version());
        return saved;
    }

    @Override @Transactional public GovernanceResultVersion submit(Submit c){
        requireWritable(); var item=item(c.itemId()); var current=result(c.resultVersionId());
        if(item.currentResultVersionId()==null||item.currentResultVersionId()!=c.resultVersionId()||current.version()!=c.expectedResultVersion()) throw new GovernanceVersionConflictException("治理结果已变化，请刷新后重试");
        if(current.status()!=GovernanceResultStatus.DRAFT) throw new GovernanceConflictException("治理结果已经提交");
        var saved=new GovernanceResultVersion(current.id(),current.itemId(),current.governanceRound(),current.resultVersion(),current.field(),current.originalValueJson(),current.proposedValueJson(),current.standardVersion(),current.dictionaryVersions(),GovernanceResultStatus.SUBMITTED,current.reworkReason(),c.actorUserId(),current.savedAt(),c.submittedAt(),current.version()+1);
        updateResult(saved,current.version()); updateItem(copy(item,GovernanceItemStatus.SUBMITTED,item.version()+1,saved.id(),item.blockReason(),item.governanceRound(),item.reworkSourceItemId()),item.version()); return saved;
    }

    @Override @Transactional public GovernanceResultVersion openRework(OpenRework c){
        requireWritable(); var item=item(c.itemId()); requireVersion(item,c.expectedItemVersion()); var current=currentResult(item.id());
        if(item.status()!=GovernanceItemStatus.REWORK_REQUIRED||current==null||current.status()!=GovernanceResultStatus.SUBMITTED) throw new GovernanceConflictException("只有具备已提交结果的待返工治理项可以开启新轮次");
        var superseded=new GovernanceResultVersion(current.id(),current.itemId(),current.governanceRound(),current.resultVersion(),current.field(),current.originalValueJson(),current.proposedValueJson(),current.standardVersion(),current.dictionaryVersions(),GovernanceResultStatus.SUPERSEDED,current.reworkReason(),current.actorUserId(),current.savedAt(),current.submittedAt(),current.version()+1); updateResult(superseded,current.version());
        var key=new GeneratedKeyHolder(); jdbc.sql("INSERT INTO governance_result_version (item_id,governance_round,result_version,status,version,payload_json) VALUES (:item,:round,:resultVersion,'DRAFT',0,'{}')").param("item",item.id()).param("round",c.governanceRound()).param("resultVersion",current.resultVersion()+1).update(key,"id");
        var draft=new GovernanceResultVersion(key.getKeyAs(Long.class),item.id(),c.governanceRound(),current.resultVersion()+1,current.field(),current.originalValueJson(),current.proposedValueJson(),current.standardVersion(),current.dictionaryVersions(),GovernanceResultStatus.DRAFT,c.reason(),c.actorUserId(),c.openedAt(),null,0); jdbc.sql("UPDATE governance_result_version SET payload_json=:p WHERE id=:id").param("p",encode(draft)).param("id",draft.id()).update();
        updateItem(copy(item,GovernanceItemStatus.PROCESSING,item.version()+1,draft.id(),null,c.governanceRound(),item.reworkSourceItemId()==null?item.id():item.reworkSourceItemId()),item.version()); return draft;
    }

    @Override public GovernanceResultVersion markApplied(long id,long expected){ requireWritable(); var current=result(id); if(current.version()!=expected) throw new GovernanceVersionConflictException("治理结果已变化，请刷新后重试"); if(current.status()==GovernanceResultStatus.APPLIED)return current; if(current.status()!=GovernanceResultStatus.SUBMITTED)throw new GovernanceConflictException("只有已提交结果可以正式应用"); var changed=new GovernanceResultVersion(current.id(),current.itemId(),current.governanceRound(),current.resultVersion(),current.field(),current.originalValueJson(),current.proposedValueJson(),current.standardVersion(),current.dictionaryVersions(),GovernanceResultStatus.APPLIED,current.reworkReason(),current.actorUserId(),current.savedAt(),current.submittedAt(),current.version()+1); updateResult(changed,expected); return changed; }
    @Override public GovernanceItem updateItemStatus(long id,GovernanceItemStatus status,String reason){ requireWritable(); var current=item(id); var changed=copy(current,status,current.version()+1,current.currentResultVersionId(),reason,current.governanceRound(),current.reworkSourceItemId()); updateItem(changed,current.version()); return changed; }
    @Override @Transactional public List<GovernanceItem> updateItemStatuses(Map<Long,GovernanceItemStatus> statuses){ return statuses.entrySet().stream().map(e->updateItemStatus(e.getKey(),e.getValue(),null)).toList(); }

    private GovernanceResultVersion result(long id){return jdbc.sql("SELECT payload_json FROM governance_result_version WHERE id=:id").param("id",id).query(String.class).optional().map(v->decode(v,GovernanceResultVersion.class)).orElseThrow(()->new IllegalArgumentException("治理结果不存在"));}
    private void requireVersion(GovernanceItem item,long expected){if(item.version()!=expected)throw new GovernanceVersionConflictException("治理项已变化，请刷新后重试");}
    private void updateResult(GovernanceResultVersion value,long expected){int n=jdbc.sql("UPDATE governance_result_version SET status=:status,version=version+1,payload_json=:payload WHERE id=:id AND version=:expected").param("status",value.status().name()).param("payload",encode(value)).param("id",value.id()).param("expected",expected).update();requireUpdated(n,()->new GovernanceVersionConflictException("治理结果已变化，请刷新后重试"));}
    private void updateItem(GovernanceItem value,long expected){int n=jdbc.sql("UPDATE governance_item SET status=:status,governance_round=:round,current_result_version_id=:result,version=version+1,payload_json=:payload WHERE id=:id AND version=:expected").param("status",value.status().name()).param("round",value.governanceRound()).param("result",value.currentResultVersionId()).param("payload",encode(value)).param("id",value.id()).param("expected",expected).update();requireUpdated(n,()->new GovernanceVersionConflictException("治理项已变化，请刷新后重试"));}
    private GovernanceItem copy(GovernanceItem i,GovernanceItemStatus s,long v,Long result,String reason,int round,Long source){return new GovernanceItem(i.id(),i.taskId(),i.planId(),i.issueId(),i.assetId(),i.targetField(),i.actionType(),i.responsibleUserId(),s,i.assetVersion(),round,i.scopeFingerprint(),v,result,reason,source);}
}
