package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.confirmation.application.AssetResponsibilityPort;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "oceanbase"})
@ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "true")
public class JdbcAssetResponsibilityAdapter implements AssetResponsibilityPort {

    private final JdbcClient jdbc;

    public JdbcAssetResponsibilityAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AssetResponsibility> currentResponsibility(long assetId) {
        return jdbc.sql("SELECT drawing_id, responsible_user_id, responsibility_scope "
                        + "FROM asset_responsibility_ext WHERE drawing_id=:id AND active=1 ORDER BY id DESC LIMIT 1")
                .param("id", assetId)
                .query((row, n) -> new AssetResponsibility(
                        row.getLong("drawing_id"), row.getString("responsible_user_id"),
                        row.getString("responsibility_scope")))
                .optional();
    }

    @Override
    public AssetResponsibility assign(long assetId, String responsibleUserId, String responsibilityScope) {
        jdbc.sql("UPDATE asset_responsibility_ext SET active = 0, updated_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE drawing_id = :id AND active = 1")
                .param("id", assetId).update();
        jdbc.sql("INSERT INTO asset_responsibility_ext "
                        + "(drawing_id, responsible_user_id, responsibility_scope, active) "
                        + "VALUES (:id, :uid, :scope, 1)")
                .param("id", assetId).param("uid", responsibleUserId).param("scope", responsibilityScope)
                .update();
        return new AssetResponsibility(assetId, responsibleUserId, responsibilityScope);
    }
}
