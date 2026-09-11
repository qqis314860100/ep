package com.tianshu.assets.asset.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class OceanBaseAssetRepositoryTest {
    private OceanBaseAssetRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:asset-repository;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var sql = new JdbcTemplate(dataSource);
        sql.execute("DROP ALL OBJECTS");
        sql.execute("""
                CREATE TABLE sys_drawing(
                  id BIGINT PRIMARY KEY, drawing_title VARCHAR(200), drawing_content VARCHAR(500),
                  drawing_url VARCHAR(255), drawing_img VARCHAR(255), drawing_column VARCHAR(255),
                  drawing_label VARCHAR(255), drawing_platform VARCHAR(100), drawing_line VARCHAR(100),
                  drawing_format VARCHAR(40), created_by_name VARCHAR(100), last_update_date TIMESTAMP)
                """);
        sql.execute("""
                CREATE TABLE asset_package_ext(
                  drawing_id BIGINT PRIMARY KEY, asset_number VARCHAR(100), asset_type VARCHAR(40),
                  status VARCHAR(40), module_tags VARCHAR(255), standard_equipment_module BOOLEAN,
                  linked_module_asset_ids VARCHAR(255), equipment_interconnect_code VARCHAR(100),
                  owner_department VARCHAR(100), version BIGINT, updated_at TIMESTAMP)
                """);
        sql.execute("""
                CREATE TABLE asset_scope_ext(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, drawing_id BIGINT, platform_family VARCHAR(100),
                  platform_variant VARCHAR(100), product_line VARCHAR(100), base_name VARCHAR(100),
                  production_line VARCHAR(100), process_section VARCHAR(100))
                """);
        sql.execute("""
                CREATE TABLE asset_file_ext(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, drawing_id BIGINT, original_name VARCHAR(255),
                  display_name VARCHAR(255), format VARCHAR(40), role VARCHAR(100), storage_key VARCHAR(255),
                  content_sha256 VARCHAR(100), size_bytes BIGINT, previewable BOOLEAN, is_primary BOOLEAN,
                  file_status VARCHAR(40))
                """);
        sql.update("""
                INSERT INTO sys_drawing VALUES
                (131, '浏览器更新草稿链路', '真实数据库测试', '', '', '[\"机械\"]', '[]',
                 '乘用车', 'A 拉线', 'PDF', '系统管理员', CURRENT_TIMESTAMP)
                """);
        sql.update("""
                INSERT INTO asset_package_ext VALUES
                (131, 'E2E-BROWSER-20260911-002', 'MIXED_ASSET', 'PENDING_CURATION', '[]', FALSE,
                 '[]', '', '信息化部', 2, CURRENT_TIMESTAMP)
                """);
        sql.update("""
                INSERT INTO asset_file_ext
                (drawing_id, original_name, display_name, format, role, storage_key, content_sha256,
                 size_bytes, previewable, is_primary, file_status)
                VALUES (131, 'E2E-assembly-preview.pdf', 'E2E-assembly-preview.pdf', 'PDF', '二维图纸',
                        'stored-key', 'abc', 128, TRUE, TRUE, 'AVAILABLE')
                """);
        var jdbc = JdbcClient.create(dataSource);
        var objectMapper = new ObjectMapper();
        repository = new OceanBaseAssetRepository(jdbc,
                new OceanBaseAssetExtensionStore(jdbc, objectMapper, true), objectMapper, true);
    }

    @Test
    void searchesByAssetNumberStoredInTheExtensionTable() {
        var criteria = new AssetSearchCriteria(
                "E2E-BROWSER-20260911-002", null, null, "", "", "", "", "", false,
                1, 20, "", "", "", "", null, null, false, "RELEVANCE");

        var result = repository.search(criteria);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).singleElement()
                .extracting(asset -> asset.assetNumber())
                .isEqualTo("E2E-BROWSER-20260911-002");
    }

    @Test
    void searchesByOriginalFileNameStoredInTheExtensionTable() {
        var criteria = new AssetSearchCriteria(
                "assembly-preview", null, null, "", "", "", "", "", false,
                1, 20, "", "", "", "", null, null, false, "RELEVANCE");

        var result = repository.search(criteria);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).singleElement()
                .extracting(asset -> asset.id())
                .isEqualTo(131L);
    }
}
