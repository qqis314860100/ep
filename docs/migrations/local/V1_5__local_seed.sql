-- Local development data. Uses INSERT IGNORE so existing rows are never overwritten.
USE tianshu;

INSERT IGNORE INTO temp_person
    (id, code, name, email, status, created_by, last_updated_by)
VALUES
    (1, 'demo-user', '陈工', 'demo@example.local', 1, -1, -1),
    (2, 'emp-li', '李工', 'li@example.local', 1, -1, -1),
    (3, 'emp-wang', '王工', 'wang@example.local', 1, -1, -1),
    (4, 'emp-chen', '陈工', 'chen@example.local', 1, -1, -1);

INSERT IGNORE INTO sys_drawing
    (id, drawing_title, drawing_content, drawing_url, drawing_img, drawing_column, drawing_format,
     drawing_label, drawing_platform, drawing_line, created_by, created_by_name, last_update_date)
VALUES
    (101, '焊接工位总成数模', 'A 拉线焊接工位设备总成和工装空间模型。', 'welding-station.x_t', '["preview.png"]', '["机械","工装"]', 'X_T', '["设备数模","焊接"]', '乘用车', 'A 拉线', 1, '陈工', '2026-07-18 10:00:00'),
    (102, '定位工装数模', '焊接模块使用的定位工装三维模型。', 'fixture.step', NULL, '["机械","工装"]', 'STEP', '["工装","定位"]', '乘用车', 'A 拉线', 2, '李工', '2026-07-17 17:30:00'),
    (103, '输送模块布置数模', '焊接工位前后输送模块布置和接口空间。', 'conveyor.stp', '["conveyor.jpg"]', '["机械"]', 'STP', '["输送"]', '乘用车', 'A 拉线', 3, '王工', '2026-07-16 15:20:00'),
    (104, 'XM-PL01 设备图', '历史资料，基地和标准范围待补充。', 'legacy-layout.pdf', NULL, '["机械"]', 'PDF', '["历史资料"]', 'H03底部水冷', 'XM-PL01', 1, '赵工', '2026-07-12 11:10:00'),
    (105, 'PACK 段设备接口图', 'B 拉线 PACK 段设备接口二维图纸。', 'pack-interface.pdf', NULL, '["机械","电气"]', 'PDF', '["设备接口"]', '商用车', 'B 拉线', 1, '周工', '2026-07-10 19:45:00');

INSERT IGNORE INTO asset_package_ext
    (drawing_id, asset_number, asset_type, status, module_tags, standard_equipment_module,
     linked_module_asset_ids, equipment_interconnect_code, owner_user_id, owner_department)
VALUES
    (101, 'DM-ND-A-0001', 'MIXED_ASSET', 'STANDARDIZED', '["标准设备模块"]', 1, '[102,103]', 'EQ-ND-A-001', 'demo-user', '设备工程部'),
    (102, 'DM-ND-A-0002', 'THREE_DIMENSIONAL_MODEL', 'STANDARDIZED', '["定位模块"]', 1, '[]', 'EQ-ND-A-002', 'emp-li', '工艺仿真组'),
    (103, 'DM-ND-A-0003', 'MIXED_ASSET', 'PENDING_CURATION', '["输送模块"]', 0, '[]', 'EQ-ND-A-003', 'emp-wang', '设备工程部'),
    (104, 'LEGACY-00000104', 'TWO_DIMENSIONAL_DRAWING', 'PENDING_CURATION', '[]', 0, '[]', NULL, 'demo-user', '设备工程部'),
    (105, 'DM-LY-B-0012', 'TWO_DIMENSIONAL_DRAWING', 'STANDARDIZED', '[]', 0, '[]', 'EQ-LY-B-012', 'demo-user', '自动化部');

INSERT IGNORE INTO asset_scope_ext
    (id, drawing_id, platform_family, platform_variant, product_line, base_name, production_line, process_section)
VALUES
    (1001, 101, '乘用车', '大面水冷', 'H03', '宁德基地', 'A 拉线', '焊接段'),
    (1002, 101, '乘用车', '大面水冷', 'H03', '溧阳基地', 'B 拉线', '焊接段'),
    (1003, 102, '乘用车', '底部水冷', 'H03', '宁德基地', 'A 拉线', '焊接段'),
    (1004, 103, '乘用车', '大面水冷', 'H03', '宁德基地', 'A 拉线', '焊接段'),
    (1005, 104, '乘用车', '底部水冷', NULL, NULL, 'XM-PL01', NULL),
    (1006, 105, '商用车', NULL, 'P02', '溧阳基地', 'B 拉线', 'PACK 段');

INSERT IGNORE INTO asset_file_ext
    (id, drawing_id, original_name, display_name, format, role, storage_key, size_bytes, previewable, is_primary)
VALUES
    (1001, 101, 'welding-station.x_t', '焊接工位总成源模型', 'X_T', '三维源模型', NULL, 428500000, 0, 1),
    (1002, 101, 'welding-layout.pdf', '焊接工位布置图', 'PDF', '二维图纸', NULL, 12800000, 1, 0),
    (1003, 101, 'preview.png', '焊接工位预览图', 'PNG', '预览文件', NULL, 2400000, 1, 0),
    (1010, 102, 'fixture.step', '定位工装源模型', 'STEP', '三维源模型', NULL, 86000000, 0, 1),
    (1020, 103, 'conveyor.stp', '输送模块源模型', 'STP', '三维源模型', NULL, 124000000, 0, 1),
    (1021, 103, 'conveyor.jpg', '输送模块预览图', 'JPG', '预览文件', NULL, 1800000, 1, 0),
    (1030, 104, 'legacy-layout.pdf', '历史设备图', 'PDF', '二维图纸', NULL, 8400000, 1, 1),
    (1040, 105, 'pack-interface.pdf', 'PACK 设备接口图', 'PDF', '二维图纸', NULL, 6200000, 1, 1);

INSERT IGNORE INTO asset_module_link_ext
    (id, source_drawing_id, target_drawing_id, link_type, description, created_by)
VALUES
    (1, 101, 102, 'REFERENCES', '焊接总成引用该定位工装。', 'demo-user'),
    (2, 101, 103, 'CONTAINS', '整线总成包含输送模块。', 'demo-user');

INSERT IGNORE INTO asset_equipment_interconnect_ext
    (id, drawing_id, equipment_code, equipment_name, base_name, production_line, process_section, interconnect_data_ref)
VALUES
    (1, 101, 'EQ-ND-A-001', '焊接工位总成', '宁德基地', 'A 拉线', '焊接段', '/line-data/EQ-ND-A-001'),
    (2, 102, 'EQ-ND-A-002', '定位工装设备', '宁德基地', 'A 拉线', '焊接段', '/line-data/EQ-ND-A-002'),
    (3, 103, 'EQ-ND-A-003', '输送模块设备', '宁德基地', 'A 拉线', '焊接段', '/line-data/EQ-ND-A-003'),
    (4, 105, 'EQ-LY-B-012', 'PACK 接口设备', '溧阳基地', 'B 拉线', 'PACK 段', '/line-data/EQ-LY-B-012');

INSERT IGNORE INTO governance_task
    (id, task_number, name, status, scope_description, owner_user_id, owner_name, assignee_id,
     due_date, target_quantity, completed_quantity, quantity_unit)
VALUES
    (1, 'GOV-2026-001', 'A 拉线历史数模范围补充', 'IN_PROGRESS', '旧拉线：XM-PL01、A线', 'emp-chen', '陈工', 'emp-chen', '2026-08-15', 286, 174, '个资产'),
    (2, 'GOV-2026-002', '历史专业类别标准化', 'PENDING_CONFIRMATION', '机械、电气自由文本', 'emp-li', '李工', 'emp-li', '2026-07-31', 421, 421, '个字段'),
    (3, 'GOV-2026-003', '失效文件引用治理', 'COMPLETED', '无法访问的对象存储文件', 'emp-wang', '王工', 'emp-wang', '2026-07-25', 37, 37, '个文件');

INSERT IGNORE INTO governance_task
    (id, task_number, name, status, scope_description, issue_type, owner_user_id, owner_name,
     assignee_id, due_date, target_quantity, completed_quantity, quantity_unit, workflow_version, current_round)
VALUES
    (12, 'GOV-2026-012', '历史说明字段闭环治理', 'DRAFT', 'FIELD_SUPPLEMENT', 'MISSING_FIELD',
     'emp-chen', '陈工', 'emp-chen', '2026-08-31', 1, 0, '个字段', 'CLOSED_LOOP_V1', 1);

INSERT IGNORE INTO governance_issue
    (id, asset_id, target_field, issue_type, target_path, rule_code, rule_version,
     original_fact_json, asset_version, scope_fingerprint, severity, blocking, status,
     task_id, fingerprint, version)
VALUES
    (1201, 104, 'DESCRIPTION', 'MISSING_FIELD', '$.standardDescription', 'FIELD-COMPLETENESS', 1,
     '{"drawingContent":"历史设备接口图"}', 0, 'asset:104:description', 'MEDIUM', 0,
     'CLAIMED', 12, '104:DESCRIPTION:FIELD-COMPLETENESS:1', 1);

INSERT IGNORE INTO governance_plan
    (id, task_id, sequence_number, name, responsible_user_id, start_date, due_date, target_quantity,
     completed_quantity, quantity_unit, status, completed_at, actual_start, actual_end, dependency_ids)
VALUES
    (101, 1, 1, '导出历史模组资产清单', 'emp-chen', '2026-08-01', '2026-08-02', 286, 286, '个资产', 'DONE', '2026-07-10', '2026-08-01', '2026-08-02', '[]'),
    (102, 1, 2, '补充平台、基地和拉线范围', 'emp-chen', '2026-08-03', '2026-08-09', 286, 174, '个资产', 'IN_PROGRESS', NULL, '2026-08-03', NULL, '[101]'),
    (103, 1, 3, '提交业务专家确认', 'emp-li', '2026-08-10', '2026-08-12', 174, 0, '个资产', 'TODO', NULL, NULL, NULL, '[102]'),
    (201, 2, 1, '整理历史专业自由文本', 'emp-li', '2026-07-08', '2026-07-12', 421, 421, '个字段', 'DONE', '2026-07-12', '2026-07-08', '2026-07-12', '[]'),
    (202, 2, 2, '提交标准化结果确认', 'emp-li', '2026-07-13', '2026-07-15', 421, 421, '个字段', 'DONE', '2026-07-15', '2026-07-13', '2026-07-15', '[201]'),
    (301, 3, 1, '检查对象存储文件可访问性', 'emp-wang', '2026-07-16', '2026-07-17', 37, 37, '个文件', 'DONE', '2026-07-17', '2026-07-16', '2026-07-17', '[]'),
    (302, 3, 2, '登记失效引用处理结论', 'emp-wang', '2026-07-18', '2026-07-18', 37, 37, '个文件', 'DONE', '2026-07-18', '2026-07-18', '2026-07-18', '[301]');

INSERT IGNORE INTO dictionary_item
    (id, category_code, item_code, item_name, parent_id, sort_order, usage_count,
     forward_name, reverse_name, directional, allow_duplicate)
VALUES
    (1, 'PLATFORM_FAMILY', 'PASSENGER', '乘用车', NULL, 10, 5, NULL, NULL, 0, 0),
    (2, 'PLATFORM_FAMILY', 'COMMERCIAL', '商用车', NULL, 20, 1, NULL, NULL, 0, 0),
    (3, 'PLATFORM_FAMILY', 'ENERGY_STORAGE', '储能', NULL, 30, 0, NULL, NULL, 0, 0),
    (4, 'PLATFORM_FAMILY', 'MODULE', '模组', NULL, 40, 0, NULL, NULL, 0, 0),
    (5, 'PLATFORM_FAMILY', 'CYLINDRICAL', '圆柱', NULL, 50, 0, NULL, NULL, 0, 0),
    (11, 'PLATFORM_VARIANT', 'PASSENGER_SURFACE_COOLING', '大面水冷', 1, 10, 3, NULL, NULL, 0, 0),
    (12, 'PLATFORM_VARIANT', 'PASSENGER_BOTTOM_COOLING', '底部水冷', 1, 20, 2, NULL, NULL, 0, 0),
    (13, 'PLATFORM_VARIANT', 'COMMERCIAL', '商用车', 2, 10, 1, NULL, NULL, 0, 0),
    (14, 'PLATFORM_VARIANT', 'STORAGE_CONTAINER', '集装箱', 3, 10, 0, NULL, NULL, 0, 0),
    (15, 'PLATFORM_VARIANT', 'STORAGE_BOX', '电箱', 3, 20, 0, NULL, NULL, 0, 0),
    (16, 'PLATFORM_VARIANT', 'STORAGE_CABINET', '电柜', 3, 30, 0, NULL, NULL, 0, 0),
    (17, 'PLATFORM_VARIANT', 'MODULE', '模组', 4, 10, 0, NULL, NULL, 0, 0),
    (18, 'PLATFORM_VARIANT', 'CYLINDRICAL', '圆柱', 5, 10, 0, NULL, NULL, 0, 0),
    (21, 'PRODUCT_LINE', 'H03_BOTTOM', 'H03', 12, 10, 2, NULL, NULL, 0, 0),
    (22, 'PRODUCT_LINE', 'P02', 'P02', 13, 10, 1, NULL, NULL, 0, 0),
    (23, 'PRODUCT_LINE', 'H03_SURFACE', 'H03', 11, 10, 3, NULL, NULL, 0, 0),
    (24, 'PRODUCT_LINE', 'M01', 'M01', 17, 10, 0, NULL, NULL, 0, 0),
    (25, 'PRODUCT_LINE', 'P01', 'P01', 18, 10, 0, NULL, NULL, 0, 0),
    (101, 'BASE', 'NINGDE', '宁德基地', NULL, 10, 4, NULL, NULL, 0, 0),
    (102, 'BASE', 'LIYANG', '溧阳基地', NULL, 20, 2, NULL, NULL, 0, 0),
    (111, 'PRODUCTION_LINE', 'NINGDE_A', 'A 拉线', 101, 10, 4, NULL, NULL, 0, 0),
    (112, 'PRODUCTION_LINE', 'LIYANG_B', 'B 拉线', 102, 10, 2, NULL, NULL, 0, 0),
    (121, 'PROCESS_SECTION', 'WELDING', '焊接段', 111, 10, 3, NULL, NULL, 0, 0),
    (122, 'PROCESS_SECTION', 'PACK', 'PACK 段', 112, 10, 1, NULL, NULL, 0, 0),
    (201, 'SPECIALTY', 'MECHANICAL', '机械', NULL, 10, 5, NULL, NULL, 0, 0),
    (202, 'SPECIALTY', 'ELECTRICAL', '电气', NULL, 20, 1, NULL, NULL, 0, 0),
    (203, 'SPECIALTY', 'HYDRAULIC', '液压', NULL, 30, 0, NULL, NULL, 0, 0),
    (204, 'SPECIALTY', 'PNEUMATIC', '气动', NULL, 40, 0, NULL, NULL, 0, 0),
    (205, 'SPECIALTY', 'TOOLING', '工装', NULL, 50, 3, NULL, NULL, 0, 0),
    (211, 'TAG', 'EQUIPMENT_MODEL', '设备数模', NULL, 10, 2, NULL, NULL, 0, 0),
    (212, 'TAG', 'WELDING', '焊接', NULL, 20, 1, NULL, NULL, 0, 0),
    (213, 'TAG', 'FIXTURE', '工装', NULL, 30, 1, NULL, NULL, 0, 0),
    (221, 'MODULE_TAG', 'STANDARD_MODULE', '标准设备模块', NULL, 10, 2, NULL, NULL, 0, 0),
    (222, 'MODULE_TAG', 'POSITIONING_MODULE', '定位模块', NULL, 20, 1, NULL, NULL, 0, 0),
    (223, 'MODULE_TAG', 'CONVEYOR_MODULE', '输送模块', NULL, 30, 1, NULL, NULL, 0, 0),
    (231, 'ASSET_TYPE', 'THREE_DIMENSIONAL_MODEL', '三维模型', NULL, 10, 2, NULL, NULL, 0, 0),
    (232, 'ASSET_TYPE', 'TWO_DIMENSIONAL_DRAWING', '二维图纸', NULL, 20, 2, NULL, NULL, 0, 0),
    (233, 'ASSET_TYPE', 'MIXED_ASSET', '混合资产', NULL, 30, 2, NULL, NULL, 0, 0),
    (234, 'ASSET_TYPE', 'OTHER', '其他资料', NULL, 40, 0, NULL, NULL, 0, 0),
    (241, 'FILE_ROLE', 'THREE_DIMENSIONAL_SOURCE', '三维源模型', NULL, 10, 3, NULL, NULL, 0, 0),
    (242, 'FILE_ROLE', 'TWO_DIMENSIONAL_DRAWING', '二维图纸', NULL, 20, 3, NULL, NULL, 0, 0),
    (243, 'FILE_ROLE', 'PREVIEW_FILE', '预览文件', NULL, 30, 2, NULL, NULL, 0, 0),
    (244, 'FILE_ROLE', 'INSTRUCTION', '说明附件', NULL, 40, 0, NULL, NULL, 0, 0),
    (245, 'FILE_ROLE', 'OTHER_ATTACHMENT', '其他附件', NULL, 50, 0, NULL, NULL, 0, 0),
    (251, 'RELATION_TYPE', 'CONTAINS', '包含', NULL, 10, 2, '包含', '属于', 1, 0),
    (252, 'RELATION_TYPE', 'REFERENCES', '引用', NULL, 20, 3, '引用', '被引用', 1, 1),
    (253, 'RELATION_TYPE', 'MATCHES', '配套', NULL, 30, 1, '配套', '配套', 0, 1),
    (254, 'RELATION_TYPE', 'REPLACES', '替代', NULL, 40, 0, '替代', '被替代', 1, 0);

INSERT IGNORE INTO dictionary_item
    (id, category_code, item_code, item_name, parent_id, sort_order, usage_count,
     forward_name, reverse_name, directional, allow_duplicate)
VALUES
    (261, 'DOCUMENT_CATEGORY', 'TECHNICAL_SPECIFICATION', '技术规范', NULL, 10, 0, NULL, NULL, 0, 0),
    (262, 'DOCUMENT_CATEGORY', 'MANUAL', '说明书', NULL, 20, 0, NULL, NULL, 0, 0),
    (263, 'DOCUMENT_CATEGORY', 'WORK_INSTRUCTION', '作业指导书', NULL, 30, 0, NULL, NULL, 0, 0),
    (264, 'DOCUMENT_CATEGORY', 'COMMISSIONING', '调试资料', NULL, 40, 0, NULL, NULL, 0, 0),
    (265, 'DOCUMENT_CATEGORY', 'ACCEPTANCE', '验收资料', NULL, 50, 0, NULL, NULL, 0, 0),
    (266, 'DOCUMENT_CATEGORY', 'STANDARD_TEMPLATE', '标准模板', NULL, 60, 0, NULL, NULL, 0, 0);
