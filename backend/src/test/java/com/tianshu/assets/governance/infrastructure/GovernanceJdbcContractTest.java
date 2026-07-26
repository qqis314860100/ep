package com.tianshu.assets.governance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore.SaveDraft;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore.Submit;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore.FreezeCommand;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import com.tianshu.assets.governance.task.domain.GovernanceScopeItem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class GovernanceJdbcContractTest {
    private JdbcTemplate sql;
    private JdbcGovernanceWorkflowStore workflow;
    private JdbcGovernanceExecutionStore execution;
    private JdbcGovernanceAssetAdapter assets;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("governance-contract;MODE=MySQL;DB_CLOSE_DELAY=-1").build();
        sql = new JdbcTemplate(dataSource);
        sql.execute("DROP ALL OBJECTS");
        sql.execute("CREATE TABLE governance_rule_snapshot(id BIGINT AUTO_INCREMENT PRIMARY KEY,task_id BIGINT,payload_json CLOB)");
        sql.execute("CREATE TABLE governance_scope_snapshot(id BIGINT AUTO_INCREMENT PRIMARY KEY,task_id BIGINT,created_by VARCHAR(100),frozen_at TIMESTAMP,item_count INT,payload_json CLOB)");
        sql.execute("CREATE TABLE governance_scope_item(id BIGINT AUTO_INCREMENT PRIMARY KEY,snapshot_id BIGINT,task_id BIGINT,issue_id BIGINT,asset_id BIGINT,target_field VARCHAR(40),payload_json CLOB)");
        sql.execute("CREATE TABLE governance_item(id BIGINT AUTO_INCREMENT PRIMARY KEY,task_id BIGINT,issue_id BIGINT,asset_id BIGINT,status VARCHAR(40),governance_round INT,current_result_version_id BIGINT,version BIGINT,payload_json CLOB)");
        sql.execute("CREATE TABLE governance_result_version(id BIGINT AUTO_INCREMENT PRIMARY KEY,item_id BIGINT,governance_round INT,result_version INT,status VARCHAR(40),version BIGINT,payload_json CLOB)");
        sql.execute("CREATE TABLE sys_drawing(id BIGINT PRIMARY KEY,drawing_content CLOB)");
        sql.execute("CREATE TABLE asset_package_ext(drawing_id BIGINT PRIMARY KEY,status VARCHAR(40),standard_description CLOB,standard_specialties CLOB,standard_value_updated_at TIMESTAMP,version BIGINT,updated_at TIMESTAMP)");
        sql.execute("CREATE TABLE asset_audit_ext(id BIGINT AUTO_INCREMENT PRIMARY KEY,drawing_id BIGINT,actor_user_id VARCHAR(100),action VARCHAR(100),payload_json CLOB)");
        sql.update("INSERT INTO sys_drawing VALUES (104,'历史设备接口图原值')");
        sql.update("INSERT INTO asset_package_ext(drawing_id,status,version) VALUES (104,'PENDING_CURATION',0)");
        var jdbc = JdbcClient.create(dataSource);
        var json = new ObjectMapper().findAndRegisterModules();
        workflow = new JdbcGovernanceWorkflowStore(jdbc, json, true);
        execution = new JdbcGovernanceExecutionStore(jdbc, json, true);
        assets = new JdbcGovernanceAssetAdapter(jdbc, json, true);
    }

    @Test
    void jdbcStoresRoundTripAFieldClosureWithoutChangingLegacySource() {
        var original = sql.queryForObject("SELECT drawing_content FROM sys_drawing WHERE id=104", String.class);
        var rule = new GovernanceRuleSnapshot(0,"FIELD-COMPLETENESS",1,1,Map.of(),"FIELD-QUALITY",1);
        var scopeItem = new GovernanceScopeItem(0,12,1,1201,104,GovernanceField.DESCRIPTION,
                "$.standardDescription","{\"drawingContent\":\"历史设备接口图原值\"}",0,1,"104:description","emp-chen");
        var item = new GovernanceItem(0,12,1,1201,104,GovernanceField.DESCRIPTION,"FIELD_SUPPLEMENT",
                "emp-chen",GovernanceItemStatus.PENDING,0,1,"104:description",0,null,null,null);
        workflow.freeze(new FreezeCommand(12,List.of(1201L),List.of(104L),rule,"emp-chen",Instant.parse("2026-07-26T03:00:00Z"),List.of(scopeItem),List.of(item)));
        var persisted = workflow.items(12).getFirst();
        var draft = execution.saveDraft(new SaveDraft(persisted.id(),0,0,GovernanceField.DESCRIPTION,
                "\"历史设备接口图原值\"","{\"description\":\"历史设备接口图及适用说明\"}",1,Map.of(),"emp-chen",Instant.parse("2026-07-26T04:00:00Z")));
        var submitted = execution.submit(new Submit(persisted.id(),draft.id(),draft.version(),"emp-chen",Instant.parse("2026-07-26T05:00:00Z")));
        assets.applyFieldResult(persisted.id(),104,GovernanceField.DESCRIPTION,submitted.proposedValueJson(),0,"emp-chen");

        assertThat(execution.currentResult(persisted.id()).status().name()).isEqualTo("SUBMITTED");
        assertThat(sql.queryForObject("SELECT standard_description FROM asset_package_ext WHERE drawing_id=104",String.class)).isEqualTo("历史设备接口图及适用说明");
        assertThat(sql.queryForObject("SELECT drawing_content FROM sys_drawing WHERE id=104",String.class)).isEqualTo(original);
    }
}
