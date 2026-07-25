package com.tianshu.assets.document.infrastructure;

import com.tianshu.assets.document.domain.KnowledgeDocument;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;

@Repository
@Profile("oceanbase")
public class ReadOnlyDocumentRepository extends JdbcDocumentRepository {

    public ReadOnlyDocumentRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        super(jdbcTemplate, transactionManager);
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        throw new UnsupportedOperationException("OceanBase 文档仓储当前为只读");
    }

    @Override
    public KnowledgeDocument update(KnowledgeDocument document, long expectedVersion) {
        throw new UnsupportedOperationException("OceanBase 文档仓储当前为只读");
    }
}
