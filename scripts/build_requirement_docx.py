#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "requirement.md"
OUTPUT = ROOT / "仿真数模资产管理系统_产品需求文档_V1.5.docx"

BODY_FONT = "PingFang SC"
HEADING_FONT = "PingFang SC"
MONO_FONT = "Consolas"
ACCENT = "1F4E78"
LIGHT_ACCENT = "D9EAF7"
LIGHT_GRAY = "F2F2F2"


def set_run_font(run, name: str, size: float | None = None, bold: bool | None = None):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold


def set_cell_shading(cell, fill: str):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell, top=90, start=100, bottom=90, end=100):
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for tag, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn(f"w:{tag}"))
        if node is None:
            node = OxmlElement(f"w:{tag}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def mark_header_row(row):
    tr_pr = row._tr.get_or_add_trPr()
    table_header = OxmlElement("w:tblHeader")
    table_header.set(qn("w:val"), "true")
    tr_pr.append(table_header)


def add_field(paragraph, instruction: str):
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = instruction
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.extend([begin, instr, separate, end])


def add_inline(paragraph, text: str):
    token_re = re.compile(r"(\*\*.+?\*\*|`.+?`)")
    pos = 0
    for match in token_re.finditer(text):
        if match.start() > pos:
            run = paragraph.add_run(text[pos : match.start()])
            set_run_font(run, BODY_FONT)
        token = match.group(0)
        if token.startswith("**"):
            run = paragraph.add_run(token[2:-2])
            set_run_font(run, BODY_FONT, bold=True)
        else:
            run = paragraph.add_run(token[1:-1])
            set_run_font(run, MONO_FONT)
            run.font.color.rgb = RGBColor(80, 80, 80)
        pos = match.end()
    if pos < len(text):
        run = paragraph.add_run(text[pos:])
        set_run_font(run, BODY_FONT)


def configure_styles(doc: Document):
    normal = doc.styles["Normal"]
    normal.font.name = BODY_FONT
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), BODY_FONT)
    normal.font.size = Pt(10.5)
    normal.paragraph_format.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
    normal.paragraph_format.space_after = Pt(4)

    heading_specs = {
        "Title": (HEADING_FONT, 24, ACCENT, 0, 18),
        "Heading 1": (HEADING_FONT, 16, ACCENT, 12, 8),
        "Heading 2": (HEADING_FONT, 14, ACCENT, 10, 6),
        "Heading 3": (HEADING_FONT, 12, "000000", 8, 4),
        "Heading 4": (HEADING_FONT, 11, "000000", 6, 3),
    }
    for style_name, (font, size, color, before, after) in heading_specs.items():
        style = doc.styles[style_name]
        style.font.name = font
        style._element.rPr.rFonts.set(qn("w:eastAsia"), font)
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = RGBColor.from_string(color)
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.keep_with_next = True

    if "Requirement ID" not in [s.name for s in doc.styles]:
        style = doc.styles.add_style("Requirement ID", WD_STYLE_TYPE.PARAGRAPH)
        style.base_style = doc.styles["Heading 4"]
        style.font.name = HEADING_FONT
        style._element.rPr.rFonts.set(qn("w:eastAsia"), HEADING_FONT)
        style.font.size = Pt(11)
        style.font.bold = True
        style.font.color.rgb = RGBColor.from_string(ACCENT)
        style.paragraph_format.space_before = Pt(8)
        style.paragraph_format.space_after = Pt(3)
        style.paragraph_format.keep_with_next = True


def configure_page(doc: Document):
    section = doc.sections[0]
    section.page_width = Cm(21)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2.2)
    section.bottom_margin = Cm(2.0)
    section.left_margin = Cm(2.4)
    section.right_margin = Cm(2.2)
    section.header_distance = Cm(1.0)
    section.footer_distance = Cm(1.0)

    header = section.header
    p = header.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = p.add_run("仿真数模资产管理系统产品需求文档")
    set_run_font(run, BODY_FONT, 9)
    run.font.color.rgb = RGBColor(100, 100, 100)

    footer = section.footer
    p = footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("内部资料  |  第 ")
    set_run_font(run, BODY_FONT, 9)
    add_field(p, "PAGE")
    run = p.add_run(" 页")
    set_run_font(run, BODY_FONT, 9)


def add_cover(doc: Document):
    for _ in range(4):
        doc.add_paragraph()
    title = doc.add_paragraph(style="Title")
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title.add_run("仿真数模资产管理系统").font.size = Pt(26)
    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = subtitle.add_run("产品需求文档")
    set_run_font(run, HEADING_FONT, 20, True)
    run.font.color.rgb = RGBColor.from_string(ACCENT)

    for _ in range(5):
        doc.add_paragraph()
    details = [
        ("文档版本", "V1.5"),
        ("文档状态", "需求基线"),
        ("适用终端", "PC Web"),
        ("编制日期", "2026-07-18"),
    ]
    table = doc.add_table(rows=len(details) + 1, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.style = "Table Grid"
    table.cell(0, 0).text = "文档属性"
    table.cell(0, 1).text = "内容"
    mark_header_row(table.rows[0])
    for cell in table.rows[0].cells:
        set_cell_shading(cell, ACCENT)
        set_cell_margins(cell, 120, 160, 120, 160)
        for p in cell.paragraphs:
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            for run in p.runs:
                set_run_font(run, BODY_FONT, 11, True)
                run.font.color.rgb = RGBColor(255, 255, 255)
    for row, (label, value) in zip(table.rows[1:], details):
        row.cells[0].text = label
        row.cells[1].text = value
        row.cells[0].width = Cm(4)
        row.cells[1].width = Cm(8)
        for cell in row.cells:
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            set_cell_margins(cell, 120, 160, 120, 160)
            for p in cell.paragraphs:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                for run in p.runs:
                    set_run_font(run, BODY_FONT, 11, cell == row.cells[0])
        set_cell_shading(row.cells[0], LIGHT_ACCENT)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(36)
    run = p.add_run("部门内部知识资产建设项目")
    set_run_font(run, BODY_FONT, 11)
    run.font.color.rgb = RGBColor(90, 90, 90)
    doc.add_page_break()

    toc_heading = doc.add_paragraph("目录", style="Heading 1")
    toc_heading.alignment = WD_ALIGN_PARAGRAPH.CENTER
    toc = doc.add_paragraph()
    add_field(toc, 'TOC \\o "1-3" \\h \\z \\u')
    note = doc.add_paragraph()
    note.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = note.add_run("在 Microsoft Word 中打开文档后更新目录域即可生成页码。")
    set_run_font(run, BODY_FONT, 9)
    run.font.color.rgb = RGBColor(120, 120, 120)
    doc.add_page_break()


def parse_table(lines: list[str], start: int):
    rows = []
    index = start
    while index < len(lines) and lines[index].strip().startswith("|"):
        raw = lines[index].strip().strip("|")
        rows.append([cell.strip() for cell in raw.split("|")])
        index += 1
    if len(rows) >= 2 and all(re.fullmatch(r":?-{3,}:?", cell) for cell in rows[1]):
        rows.pop(1)
    return rows, index


def add_table(doc: Document, rows: list[list[str]]):
    if not rows:
        return
    col_count = max(len(row) for row in rows)
    table = doc.add_table(rows=len(rows), cols=col_count)
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = True
    mark_header_row(table.rows[0])
    for r_idx, row in enumerate(rows):
        for c_idx in range(col_count):
            cell = table.cell(r_idx, c_idx)
            cell.text = ""
            text = row[c_idx] if c_idx < len(row) else ""
            p = cell.paragraphs[0]
            add_inline(p, text)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            set_cell_margins(cell)
            if r_idx == 0:
                set_cell_shading(cell, ACCENT)
                for run in p.runs:
                    run.font.color.rgb = RGBColor(255, 255, 255)
                    run.bold = True
            elif r_idx % 2 == 0:
                set_cell_shading(cell, LIGHT_GRAY)
    doc.add_paragraph().paragraph_format.space_after = Pt(0)


def add_body(doc: Document, markdown: str):
    lines = markdown.splitlines()
    index = 0
    skipped_source_title = False
    while index < len(lines):
        line = lines[index].rstrip()
        stripped = line.strip()
        if not stripped:
            index += 1
            continue

        if stripped.startswith("|"):
            rows, index = parse_table(lines, index)
            add_table(doc, rows)
            continue

        heading = re.match(r"^(#{1,4})\s+(.+)$", stripped)
        if heading:
            level = len(heading.group(1))
            text = heading.group(2)
            if level == 1 and not skipped_source_title:
                skipped_source_title = True
                index += 1
                continue
            style = f"Heading {min(level, 4)}"
            p = doc.add_paragraph(style=style)
            add_inline(p, text)
            index += 1
            continue

        req = re.match(r"^\*\*([A-Z]+(?:-[A-Z]+)*-\d+\s+.+?)\*\*$", stripped)
        if req:
            p = doc.add_paragraph(style="Requirement ID")
            add_inline(p, req.group(1))
            index += 1
            continue

        bullet = re.match(r"^-\s+(.+)$", stripped)
        if bullet:
            p = doc.add_paragraph(style="List Bullet")
            add_inline(p, bullet.group(1))
            index += 1
            continue

        numbered = re.match(r"^\d+\.\s+(.+)$", stripped)
        if numbered:
            p = doc.add_paragraph(style="List Number")
            add_inline(p, numbered.group(1))
            index += 1
            continue

        p = doc.add_paragraph()
        add_inline(p, stripped)
        index += 1


def set_update_fields(doc: Document):
    settings = doc.settings.element
    update_fields = settings.find(qn("w:updateFields"))
    if update_fields is None:
        update_fields = OxmlElement("w:updateFields")
        settings.append(update_fields)
    update_fields.set(qn("w:val"), "true")


def main():
    markdown = SOURCE.read_text(encoding="utf-8")
    doc = Document()
    configure_styles(doc)
    configure_page(doc)
    add_cover(doc)
    add_body(doc, markdown)
    set_update_fields(doc)

    doc.core_properties.title = "仿真数模资产管理系统产品需求文档"
    doc.core_properties.subject = "仿真数模资产标准化治理、关联展示、产品功能与用户操作需求"
    doc.core_properties.author = "项目需求组"
    doc.core_properties.keywords = "图纸管理, 生产资料, 软件需求规格说明书, SRS"
    doc.core_properties.comments = "面向产品、设计、开发、测试和外部评审的 V1.5 产品需求基线"

    doc.save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    main()
