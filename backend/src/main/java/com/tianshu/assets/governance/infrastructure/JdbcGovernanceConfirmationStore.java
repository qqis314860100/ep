package com.tianshu.assets.governance.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.application.*;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationStore;
import com.tianshu.assets.governance.confirmation.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository @Profile({"local","oceanbase"})
@ConditionalOnProperty(name="asset.governance-schema-enabled",havingValue="true")
public class JdbcGovernanceConfirmationStore extends JdbcGovernanceSupport implements GovernanceConfirmationStore {
    public JdbcGovernanceConfirmationStore(JdbcClient j,ObjectMapper o,@Value("${asset.database-writes-enabled:false}") boolean w){super(j,o,w);}
    @Override public Optional<GovernanceConfirmationRound> currentRound(long task){return rounds(task).stream().max(Comparator.comparingInt(GovernanceConfirmationRound::governanceRound));}
    @Override public List<GovernanceConfirmationRound> rounds(long task){return jdbc.sql("SELECT payload_json FROM governance_confirmation_round WHERE task_id=:id ORDER BY governance_round").param("id",task).query(String.class).list().stream().map(v->decode(v,GovernanceConfirmationRound.class)).toList();}
    @Override public GovernanceConfirmationRound round(long id){return jdbc.sql("SELECT payload_json FROM governance_confirmation_round WHERE id=:id").param("id",id).query(String.class).optional().map(v->decode(v,GovernanceConfirmationRound.class)).orElseThrow(()->new IllegalArgumentException("确认轮次不存在"));}
    @Override public GovernanceConfirmationRound createRound(long task,int round,Map<Long,Long> ids,Instant at){requireWritable();if(currentRound(task).filter(v->v.status()==GovernanceConfirmationRound.Status.PENDING).isPresent())throw new GovernanceConflictException("当前确认轮次已存在");var k=new GeneratedKeyHolder();jdbc.sql("INSERT INTO governance_confirmation_round(task_id,governance_round,status,version,payload_json) VALUES(:task,:round,'PENDING',0,'{}')").param("task",task).param("round",round).update(k,"id");var value=new GovernanceConfirmationRound(k.getKeyAs(Long.class),task,round,ids,GovernanceConfirmationRound.Status.PENDING,at,null,0);jdbc.sql("UPDATE governance_confirmation_round SET payload_json=:p WHERE id=:id").param("p",encode(value)).param("id",value.id()).update();return value;}
    @Override public void discardPendingRound(long id){requireWritable();var value=round(id);if(value.status()!=GovernanceConfirmationRound.Status.PENDING||!decisions(id).isEmpty())throw new GovernanceConflictException("只能丢弃尚未使用的待确认轮次");jdbc.sql("DELETE FROM governance_confirmation_round WHERE id=:id").param("id",id).update();}
    @Override public List<GovernanceConfirmationDecision> decisions(long id){round(id);return jdbc.sql("SELECT payload_json FROM governance_confirmation_decision WHERE round_id=:id ORDER BY item_id").param("id",id).query(String.class).list().stream().map(v->decode(v,GovernanceConfirmationDecision.class)).toList();}
    @Override public GovernanceConfirmationDecision insertDecision(GovernanceConfirmationDecision d){return insertDecisions(List.of(d)).getFirst();}
    @Override @Transactional public List<GovernanceConfirmationDecision> insertDecisions(List<GovernanceConfirmationDecision> requested){requireWritable();if(requested==null||requested.isEmpty())throw new IllegalArgumentException("确认决定不能为空");try{return requested.stream().map(d->{var k=new GeneratedKeyHolder();jdbc.sql("INSERT INTO governance_confirmation_decision(round_id,item_id,result_version_id,decision,version,payload_json) VALUES(:round,:item,:result,:decision,0,'{}')").param("round",d.roundId()).param("item",d.itemId()).param("result",d.resultVersionId()).param("decision",d.decision().name()).update(k,"id");var value=new GovernanceConfirmationDecision(k.getKeyAs(Long.class),d.roundId(),d.itemId(),d.resultVersionId(),d.decision(),d.comment(),d.confirmerUserId(),d.decidedAt(),0);jdbc.sql("UPDATE governance_confirmation_decision SET payload_json=:p WHERE id=:id").param("p",encode(value)).param("id",value.id()).update();return value;}).toList();}catch(DataIntegrityViolationException e){throw new GovernanceConflictException("本轮确认决定已保存，不能覆盖");}}
    @Override public GovernanceConfirmationRound completeRound(long id,long expected,Instant at){requireWritable();var current=round(id);if(current.status()!=GovernanceConfirmationRound.Status.PENDING)throw new GovernanceConflictException("确认轮次已经完成");var value=new GovernanceConfirmationRound(current.id(),current.taskId(),current.governanceRound(),current.resultVersionIds(),GovernanceConfirmationRound.Status.COMPLETED,current.createdAt(),at,current.version()+1);int n=jdbc.sql("UPDATE governance_confirmation_round SET status='COMPLETED',version=version+1,payload_json=:p WHERE id=:id AND version=:v").param("p",encode(value)).param("id",id).param("v",expected).update();requireUpdated(n,()->new GovernanceVersionConflictException("确认轮次已变化，请刷新后重试"));return value;}
}
