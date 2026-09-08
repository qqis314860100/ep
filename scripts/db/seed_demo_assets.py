#!/usr/bin/env python3
# 本地真库演示种子：在 tianshu 补充更丰富的图纸/模型资产（含可在线预览的真实文件）。
#
# 行为说明
# - 直接写数据库行（sys_drawing / asset_package_ext / asset_scope_ext / asset_file_ext），
#   并在 backend/.data/files 写入与 LocalFileStorage 格式一致的 <key>.bin / <key>.meta，
#   使资料检索、图集与在线预览在 local 形态下可用。
# - 幂等：若任一目标资产编号已存在则中止，可安全重跑。
# - 不删除、不覆盖既有数据；不提交生成的文件（.data 已被 gitignore）。
#
# 用法：python3 scripts/db/seed_demo_assets.py
# 凭据：优先读取仓库根 .env.local 的 DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD。

import base64
import hashlib
import json
import os
import pathlib
import re
import subprocess
import sys
import uuid

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
BACKEND = REPO_ROOT / "backend"
STORAGE_DIR = BACKEND / ".data" / "files"
DB_NAME = "tianshu"

env = dict(os.environ)
env_file = REPO_ROOT / ".env.local"
if env_file.exists():
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, _, value = line.partition("=")
            env.setdefault(key.strip(), value.strip())

DB_HOST = env.get("DB_HOST", "localhost")
DB_PORT = env.get("DB_PORT", "3306")
DB_USER = env.get("DB_USERNAME", "root")
DB_PASS = env.get("DB_PASSWORD", "")

MYSQL = ["mysql", "-h", DB_HOST, "-P", DB_PORT, "-u", DB_USER,
         "--default-character-set=utf8mb4"]


def mysql_query(sql):
    proc = subprocess.run(MYSQL + ["-N", "-B", DB_NAME, "-e", sql],
                          capture_output=True, text=True, env={**env, "MYSQL_PWD": DB_PASS})
    if proc.returncode != 0:
        raise RuntimeError(f"mysql 查询失败：{proc.stderr.strip()}")
    return proc.stdout


def mysql_exec(sql):
    proc = subprocess.run(MYSQL + [DB_NAME], input=sql, capture_output=True,
                          text=True, env={**env, "MYSQL_PWD": DB_PASS})
    if proc.returncode != 0:
        raise RuntimeError(f"mysql 执行失败：{proc.stderr.strip()}")


def q(value):
    """SQL 单引号字符串字面量。"""
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


# ---- 文件内容生成 ----

def tiny_pdf(label: str) -> bytes:
    """生成一页 ASCII 文本的最小合法 PDF（非 ASCII 字符用 _ 占位）。"""
    ascii_label = re.sub(r"[^A-Za-z0-9 _\-]", "_", label)[:40] or "DEMO"
    content = f"BT /F1 24 Tf 60 760 Td ({ascii_label}) Tj ET".encode("ascii")
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] "
         b"/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>"),
        b"<< /Length " + str(len(content)).encode() + b" >>\nstream\n" + content + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for index, obj in enumerate(objects, start=1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % index + obj + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n" % (len(objects) + 1)
    out += b"0000000000 65535 f \n"
    for offset in offsets:
        out += b"%010d 00000 n \n" % offset
    out += (b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n"
            % (len(objects) + 1, xref))
    return bytes(out)


PNG_1PX = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")


def stub_source(extension: str, note: str) -> bytes:
    text = (f"// {note}\n"
            f"// 本文件为本地联调演示种子生成的占位源文件（{extension}），"
            "非真实工程数据，仅用于上传/下载/预览链路验证。\n")
    return text.encode("utf-8")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def store_file(filename: str, content_type: str, data: bytes):
    key = str(uuid.uuid4())
    content_path = STORAGE_DIR / f"{key}.bin"
    meta_path = STORAGE_DIR / f"{key}.meta"
    content_path.write_bytes(data)
    meta_path.write_text(f"{filename}\n{content_type}\n{sha256_bytes(data)}\n",
                         encoding="utf-8")
    return key


# ---- 种子数据 ----
# (id, 资产编号, 名称, 资产类型, 状态, 专业列, 模组标签, 标准设备模块, 关联模块ids, 部门,
#  scope 列表[{platform, variant, productLine, base, line, section}], files[(文件名, 展示名, role)])
SO = "设备工程部"
SJ = "工艺仿真组"
ZD = "自动化部"

SC = [
    {"platform": "乘用车", "variant": "大面水冷", "productLine": "H03", "base": "宁德基地", "line": "A 拉线", "section": "焊接段"},
    {"platform": "乘用车", "variant": "底部水冷", "productLine": "H03", "base": "宁德基地", "line": "A 拉线", "section": "焊接段"},
    {"platform": "乘用车", "variant": "大面水冷", "productLine": "H03", "base": "溧阳基地", "line": "B 拉线", "section": "焊接段"},
    {"platform": "商用车", "variant": "商用车", "productLine": "P02", "base": "溧阳基地", "line": "B 拉线", "section": "PACK 段"},
]


def pdfs(name, label, source_ext=None):
    files = [("preview.pdf", f"{label}预览图", "预览文件", "application/pdf", tiny_pdf("DEMO " + label))]
    if source_ext:
        files.insert(0, (f"source.{source_ext}", f"{label}源模型", "三维源模型",
                         "application/octet-stream", stub_source(source_ext.upper(), name)))
    return files


SEEDS = [
    # 乘用车 宁德 A 拉线 焊接段：点焊/涂胶/总拼 工位数模（3D 源模型 + PDF/PNG 预览）
    (106, "DM-ND-A-0006", "侧围点焊工位数模", "THREE_DIMENSIONAL_MODEL", "STANDARDIZED",
     ["机械", "工装"], ["标准设备模块"], 1, [107, 108], SO, [SC[0]],
     pdfs("侧围点焊工位数模", "侧围点焊工位", "x_t")),
    (107, "DM-ND-A-0007", "侧围定位夹紧工装数模", "THREE_DIMENSIONAL_MODEL", "STANDARDIZED",
     ["机械", "工装"], ["定位模块"], 0, [], SJ, [SC[0]],
     pdfs("侧围定位夹紧工装数模", "侧围定位夹紧工装", "step")),
    (108, "DM-ND-A-0008", "侧围涂胶机器人单元数模", "MIXED_ASSET", "STANDARDIZED",
     ["机械", "电气"], [], 0, [], SO, [SC[0], SC[1]],
     pdfs("侧围涂胶机器人单元数模", "侧围涂胶机器人单元", "stp")),
    (109, "DM-ND-A-0009", "前地板总拼工位数模", "THREE_DIMENSIONAL_MODEL", "STANDARDIZED",
     ["机械"], ["标准设备模块"], 1, [110], SO, [SC[1]],
     pdfs("前地板总拼工位数模", "前地板总拼工位", "x_t")),
    (110, "DM-ND-A-0010", "前地板焊接夹具数模", "THREE_DIMENSIONAL_MODEL", "STANDARDIZED",
     ["机械", "工装"], [], 0, [], SJ, [SC[1]],
     pdfs("前地板焊接夹具数模", "前地板焊接夹具", "step")),
    (111, "DM-ND-A-0011", "机舱盖滚边压合单元数模", "MIXED_ASSET", "PENDING_CURATION",
     ["机械", "电气", "气动"], [], 0, [], SO, [SC[0]],
     pdfs("机舱盖滚边压合单元数模", "机舱盖滚边压合单元", "stp")),
    # 乘用车 溧阳 B 拉线 焊接段：后地板/门盖/电泳线
    (112, "DM-LY-B-0013", "后地板焊接工位数模", "THREE_DIMENSIONAL_MODEL", "STANDARDIZED",
     ["机械", "工装"], [], 0, [], SO, [SC[2]],
     pdfs("后地板焊接工位数模", "后地板焊接工位", "x_t")),
    (113, "DM-LY-B-0014", "门盖包边工位布置数模", "MIXED_ASSET", "STANDARDIZED",
     ["机械"], [], 0, [], SO, [SC[2]],
     pdfs("门盖包边工位布置数模", "门盖包边工位布置", "stp")),
    (114, "DM-LY-B-0015", "电泳线输送辊床数模", "MIXED_ASSET", "PENDING_CURATION",
     ["机械", "液压"], [], 0, [], SO, [SC[2]],
     pdfs("电泳线输送辊床数模", "电泳线输送辊床", "step")),
    # 商用车 溧阳 B 拉线 PACK 段：PACK 装配/测试工装图纸（PDF/DWG 图纸类为主）
    (115, "DM-LY-B-0016", "PACK 装配主线布置图", "TWO_DIMENSIONAL_DRAWING", "STANDARDIZED",
     ["机械", "电气"], [], 0, [], SO, [SC[3]],
     [("layout.dwg", "PACK 装配主线布置图(DWG)", "二维图纸", "application/octet-stream", stub_source("DWG", "PACK 装配主线布置图")),
      ("pack-mainline.pdf", "PACK 装配主线布置图", "二维图纸", "application/pdf", tiny_pdf("PACK MAINLINE"))]),
    (116, "DM-LY-B-0017", "PACK 气密测试工装图", "TWO_DIMENSIONAL_DRAWING", "STANDARDIZED",
     ["机械", "气动"], [], 0, [], SO, [SC[3]],
     [("leak-test.dwg", "PACK 气密测试工装图(DWG)", "二维图纸", "application/octet-stream", stub_source("DWG", "PACK 气密测试工装图")),
      ("leak-test.pdf", "PACK 气密测试工装图", "二维图纸", "application/pdf", tiny_pdf("PACK LEAK TEST"))]),
    (117, "DM-LY-B-0018", "PACK 模组压装设备接口图", "TWO_DIMENSIONAL_DRAWING", "PENDING_CURATION",
     ["机械", "电气"], [], 0, [], ZD, [SC[3]],
     [("module-press.pdf", "PACK 模组压装设备接口图", "二维图纸", "application/pdf", tiny_pdf("MODULE PRESS"))]),
    # 商用车电池托盘线体（补一段同基地不同段的连续资料）
    (118, "DM-LY-B-0019", "电池托盘总成焊接工位数模", "THREE_DIMENSIONAL_MODEL", "STANDARDIZED",
     ["机械", "工装"], ["标准设备模块"], 1, [119], SO, [SC[3]],
     pdfs("电池托盘总成焊接工位数模", "电池托盘总成焊接工位", "x_t")),
    (119, "DM-LY-B-0020", "电池托盘定位夹紧工装数模", "THREE_DIMENSIONAL_MODEL", "STANDARDIZED",
     ["机械", "工装"], [], 0, [], SJ, [SC[3]],
     pdfs("电池托盘定位夹紧工装数模", "电池托盘定位夹紧工装", "step")),
    (120, "DM-LY-B-0021", "托盘气密检测工位布置图", "TWO_DIMENSIONAL_DRAWING", "STANDARDIZED",
     ["机械", "气动"], [], 0, [], SO, [SC[3]],
     [("tray-leak.pdf", "托盘气密检测工位布置图", "二维图纸", "application/pdf", tiny_pdf("TRAY LEAK"))]),
    # 三维模型（X_T/STEP）为主、含 JPG 预览与说明附件
    (121, "DM-ND-A-0012", "输送线移载机构数模", "THREE_DIMENSIONAL_MODEL", "STANDARDIZED",
     ["机械"], [], 0, [], SO, [SC[0]],
     [("transfer.x_t", "输送线移载机构源模型", "三维源模型", "application/octet-stream", stub_source("X_T", "输送线移载机构数模")),
      ("transfer.png", "输送线移载机构预览图", "预览文件", "image/png", PNG_1PX)]),
    (122, "DM-ND-A-0013", "举升定位台数模", "THREE_DIMENSIONAL_MODEL", "STANDARDIZED",
     ["机械", "工装"], [], 0, [], SO, [SC[0]],
     [("lift.step", "举升定位台源模型", "三维源模型", "application/octet-stream", stub_source("STEP", "举升定位台数模")),
      ("lift.png", "举升定位台预览图", "预览文件", "image/png", PNG_1PX)]),
    (123, "DM-ND-A-0014", "焊接烟尘集尘罩布置数模", "MIXED_ASSET", "PENDING_CURATION",
     ["机械", "工装"], [], 0, [], SO, [SC[1]],
     [("dust-hood.stp", "焊接烟尘集尘罩源模型", "三维源模型", "application/octet-stream", stub_source("STP", "焊接烟尘集尘罩布置数模")),
      ("dust-hood.pdf", "焊接烟尘集尘罩布置图", "二维图纸", "application/pdf", tiny_pdf("DUST HOOD"))]),
    # 与既有 101~105 同题材的补充资料（检索相关性）
    (124, "DM-ND-A-0015", "焊接工位安全护栏布置数模", "MIXED_ASSET", "STANDARDIZED",
     ["机械", "工装"], [], 0, [], SO, [SC[0]],
     pdfs("焊接工位安全护栏布置数模", "焊接工位安全护栏", "step")),
    (125, "DM-ND-A-0016", "PACK 段模组上线输送线图", "TWO_DIMENSIONAL_DRAWING", "STANDARDIZED",
     ["机械", "电气"], [], 0, [], SO, [SC[3]],
     [("module-feed.pdf", "PACK 段模组上线输送线图", "二维图纸", "application/pdf", tiny_pdf("MODULE FEED LINE"))]),
    (126, "DM-LY-B-0022", "焊装线体工艺平面布置图", "TWO_DIMENSIONAL_DRAWING", "STANDARDIZED",
     ["机械"], [], 0, [], SO, [SC[2], SC[3]],
     [("body-shop-layout.pdf", "焊装线体工艺平面布置图", "二维图纸", "application/pdf", tiny_pdf("BODY SHOP LAYOUT"))]),
    (127, "DM-LY-B-0023", "商用车电池 PACK 测试台架图纸", "TWO_DIMENSIONAL_DRAWING", "STANDARDIZED",
     ["机械", "电气"], [], 0, [], ZD, [SC[3]],
     [("pack-testbench.pdf", "商用车电池 PACK 测试台架图纸", "二维图纸", "application/pdf", tiny_pdf("PACK TESTBENCH"))]),
]


def main():
    numbers = [seed[1] for seed in SEEDS]
    placeholders = ",".join(q(number) for number in numbers)
    existing = mysql_query(
        f"SELECT asset_number FROM asset_package_ext WHERE asset_number IN ({placeholders})").strip()
    if existing:
        print(f"中止：以下资产编号已存在，脚本幂等保护（不重复造数）：\n{existing}", file=sys.stderr)
        sys.exit(1)

    ids = [seed[0] for seed in SEEDS]
    id_placeholders = ",".join(str(seed_id) for seed_id in ids)
    existing_ids = mysql_query(
        f"SELECT id FROM sys_drawing WHERE id IN ({id_placeholders})").strip()
    if existing_ids:
        print(f"中止：以下 sys_drawing id 已被占用：{existing_ids}", file=sys.stderr)
        sys.exit(1)

    STORAGE_DIR.mkdir(parents=True, exist_ok=True)

    def j(value):
        return json.dumps(value, ensure_ascii=False)

    sql_parts = []
    for (asset_id, number, name, asset_type, status, specialties, module_tags,
         standard, linked_ids, department, scopes, files) in SEEDS:
        description = f"{name}，{department}整理归档的本地联调演示数模资料。"

        file_rows = []
        for index, (filename, display, role, content_type, data) in enumerate(files):
            previewable = role == "预览文件" or filename.lower().endswith(".pdf")
            key = store_file(filename, content_type, data)
            file_rows.append(
                f"({asset_id}, NULL, {q(filename)}, {q(display)}, {q(filename.rsplit('.', 1)[-1].upper())},"
                f" {q(role)}, {q(key)}, {q(sha256_bytes(data))}, {len(data)},"
                f" {1 if previewable else 0}, {1 if index == 0 else 0}, 'AVAILABLE')")
        sql_parts.append(
            "INSERT INTO sys_drawing (id, drawing_title, drawing_content, drawing_format,"
            " drawing_platform, drawing_line, drawing_purpose, drawing_column, drawing_label,"
            " created_by, created_by_name) VALUES "
            f"({asset_id}, {q(name)}, {q(description)}, {q(files[0][0].rsplit('.', 1)[-1].upper())},"
            f" {q(scopes[0]['platform'])}, {q(scopes[0]['line'])}, {q('本地联调演示数据')},"
            f" {q(j(specialties))}, {q(j(module_tags))}, NULL, {q('演示数据')});")
        sql_parts.append(
            "INSERT INTO asset_package_ext (drawing_id, asset_number, asset_type, status,"
            " module_tags, standard_equipment_module, linked_module_asset_ids,"
            " equipment_interconnect_code, owner_user_id, owner_department, version) VALUES "
            f"({asset_id}, {q(number)}, {q(asset_type)}, {q(status)}, {q(j(module_tags))},"
            f" {1 if standard else 0}, {q(j(linked_ids))}, {q('EQ-DEMO-' + str(asset_id))},"
            f" {q('demo-user')}, {q(department)}, 0);")
        scope_rows = ",".join(
            f"({asset_id}, {q(scope['platform'])}, {q(scope['variant'])},"
            f" {q(scope['productLine'])}, {q(scope['base'])}, {q(scope['line'])},"
            f" {q(scope['section'])}, NULL)"
            for scope in scopes)
        sql_parts.append(
            "INSERT INTO asset_scope_ext (drawing_id, platform_family, platform_variant,"
            " product_line, base_name, production_line, process_section, source_value_json)"
            f" VALUES {scope_rows};")
        sql_parts.append(
            "INSERT INTO asset_file_ext (drawing_id, legacy_file_id, original_name, display_name,"
            " format, role, storage_key, content_sha256, size_bytes, previewable, is_primary,"
            " file_status) VALUES " + ",".join(file_rows) + ";")

    mysql_exec("\n".join(sql_parts))
    print(f"完成：已写入 {len(SEEDS)} 条资产（id {ids[0]}~{ids[-1]}）及真实文件到 {STORAGE_DIR}")


if __name__ == "__main__":
    main()
