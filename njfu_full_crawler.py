#!/usr/bin/env python3
"""NJFU full schedule crawler.

This script logs in to NJFU UIA/JWXT, downloads global schedule pages
for teachers, classrooms, classes and courses, and stores both raw and
parsed data for later field/content analysis.

Dependencies:
    pip install requests beautifulsoup4 pycryptodome
"""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import json
import os
import random
import re
import sqlite3
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.parse import quote

import requests
import urllib3
from bs4 import BeautifulSoup, Tag
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad


APP_URL = "http://jwxt.njfu.edu.cn/sso.jsp"
UIA_BASE = "https://uia.njfu.edu.cn"
JWXT_BASE = "https://jwxt.njfu.edu.cn/jsxsd"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/124.0.0.0 Safari/537.36"
)

RANDOM_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"


SCHEDULE_TYPES: dict[str, dict[str, str]] = {
    "teacher": {
        "code": "jg0101",
        "label": "教师课表",
        "home_path": "kbcx/kbxx_teacher",
        "data_path": "kbcx/kbxx_teacher_ifr",
        "keyword_param": "skjs",
        "entity_field": "teacher",
    },
    "classroom": {
        "code": "jx0601",
        "label": "教室课表",
        "home_path": "kbcx/kbxx_classroom",
        "data_path": "kbcx/kbxx_classroom_ifr",
        "keyword_param": "jxcdmc",
        "entity_field": "room",
    },
    "class": {
        "code": "bj0101",
        "label": "班级课表",
        "home_path": "kbcx/kbxx_xzb",
        "data_path": "kbcx/kbxx_xzb_ifr",
        "keyword_param": "skbj",
        "entity_field": "class_name",
    },
    "course": {
        "code": "kc0101",
        "label": "课程课表",
        "home_path": "kbcx/kbxx_kc",
        "data_path": "kbcx/kbxx_kc_ifr",
        "keyword_param": "kcmc",
        "entity_field": "course_name",
    },
}


SECTION_SLOTS = {
    0: "1,2",
    1: "3,4",
    2: "5,6",
    3: "7,8",
    4: "9,10,11",
}


@dataclass
class PageRecord:
    type_key: str
    stage: str
    url: str
    method: str
    status_code: int
    final_url: str
    encoding: str
    elapsed_ms: int
    content_length: int
    file: str
    request_data: dict[str, str] | None = None


@dataclass
class CourseRecord:
    id: str
    run_id: str
    type_key: str
    type_code: str
    type_label: str
    term: str
    entity_name: str
    course_name: str
    teacher: str
    room: str
    class_name: str
    weeks_text: str
    day: int
    sections_text: str
    section_numbers: str
    slot_index: int
    table_index: int
    row_index: int
    col_index: int
    raw_text: str
    raw_html: str
    raw_lines_json: str


def random_string(length: int) -> str:
    return "".join(random.choice(RANDOM_CHARS) for _ in range(length))


def encrypt_aes(data: str, key: str) -> str:
    if not key:
        return data
    key = key.strip()
    plaintext = (random_string(64) + data).encode("utf-8")
    iv = random_string(16).encode("utf-8")
    cipher = AES.new(key.encode("utf-8"), AES.MODE_CBC, iv)
    return base64.b64encode(cipher.encrypt(pad(plaintext, AES.block_size))).decode("utf-8")


def clean_text(text: str) -> str:
    return re.sub(r"\s+", " ", text.replace("\xa0", " ")).strip()


def response_text(resp: requests.Response) -> str:
    if not resp.encoding or resp.encoding.upper() == "ISO-8859-1":
        resp.encoding = resp.apparent_encoding or "utf-8"
    return resp.text


def safe_name(value: str) -> str:
    value = re.sub(r"[^\w.-]+", "_", value, flags=re.UNICODE).strip("_")
    return value or "unnamed"


def rel(path: Path, base: Path) -> str:
    return str(path.relative_to(base)).replace("\\", "/")


class NjfuFullCrawler:
    def __init__(self, student_id: str, password: str, verify_ssl: bool, timeout: int) -> None:
        self.student_id = student_id
        self.password = password
        self.verify_ssl = verify_ssl
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": USER_AGENT})

        if not verify_ssl:
            urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

    def login(self) -> None:
        self.session.get(APP_URL, timeout=self.timeout, verify=self.verify_ssl)
        uia_url = f"{UIA_BASE}/authserver/login?service={quote(APP_URL, safe='')}"
        login_resp = self.session.get(uia_url, timeout=self.timeout, verify=self.verify_ssl)
        login_html = response_text(login_resp)
        soup = BeautifulSoup(login_html, "html.parser")

        lt = self._input_value(soup, "lt")
        salt = self._input_value(soup, "pwdDefaultEncryptSalt", by_id=True)
        dllt = self._input_value(soup, "dllt")
        execution = self._input_value(soup, "execution") or "e1s1"

        if not lt or not salt:
            raise RuntimeError("获取统一认证登录参数失败，可能是页面结构变化或网络异常")

        captcha_url = (
            f"{UIA_BASE}/authserver/needCaptcha.html"
            f"?username={quote(self.student_id)}&pwdEncrypt2=pwdEncryptSalt"
            f"&_={int(time.time() * 1000)}"
        )
        captcha_resp = self.session.get(captcha_url, timeout=self.timeout, verify=self.verify_ssl)
        if captcha_resp.text.strip().lower() != "false":
            raise RuntimeError("当前账号登录需要验证码，请先在浏览器完成一次登录或降低登录频率")

        data = {
            "username": self.student_id,
            "password": encrypt_aes(self.password, salt),
            "lt": lt,
            "dllt": dllt,
            "execution": execution,
            "_eventId": "submit",
            "rmShown": "1",
        }
        resp = self.session.post(
            uia_url,
            data=data,
            timeout=self.timeout,
            verify=self.verify_ssl,
            allow_redirects=True,
        )
        final_url = resp.url
        if "uia.njfu.edu.cn" in final_url:
            soup = BeautifulSoup(response_text(resp), "html.parser")
            msg = clean_text(soup.select_one("span#msg").get_text(" ")) if soup.select_one("span#msg") else ""
            raise RuntimeError(msg or "登录失败，请检查账号密码或统一认证状态")

    def fetch(self, url: str, method: str = "GET", data: dict[str, str] | None = None) -> requests.Response:
        started = time.perf_counter()
        if method.upper() == "POST":
            resp = self.session.post(url, data=data or {}, timeout=self.timeout, verify=self.verify_ssl)
        else:
            resp = self.session.get(url, timeout=self.timeout, verify=self.verify_ssl)
        resp.elapsed_ms = int((time.perf_counter() - started) * 1000)  # type: ignore[attr-defined]
        resp.raise_for_status()
        response_text(resp)
        return resp

    @staticmethod
    def _input_value(soup: BeautifulSoup, name_or_id: str, by_id: bool = False) -> str:
        selector = f"input#{name_or_id}" if by_id else f"input[name={name_or_id}]"
        tag = soup.select_one(selector)
        return tag.get("value", "") if tag else ""


class CrawlStorage:
    def __init__(self, output_root: Path, run_id: str) -> None:
        self.output_root = output_root
        self.run_id = run_id
        self.run_dir = output_root / run_id
        self.raw_dir = self.run_dir / "raw"
        self.tables_dir = self.run_dir / "tables"
        self.meta_dir = self.run_dir / "metadata"
        self.parsed_dir = self.run_dir / "parsed"
        for path in (self.raw_dir, self.tables_dir, self.meta_dir, self.parsed_dir):
            path.mkdir(parents=True, exist_ok=True)

        self.db_path = self.run_dir / "njfu_schedule.sqlite"
        self.conn = sqlite3.connect(self.db_path)
        self.conn.execute("PRAGMA journal_mode=WAL")
        self._init_db()

    def close(self) -> None:
        self.conn.commit()
        self.conn.close()

    def save_response(
        self,
        type_key: str,
        stage: str,
        method: str,
        resp: requests.Response,
        request_data: dict[str, str] | None = None,
    ) -> tuple[str, str]:
        file_path = self.raw_dir / f"{safe_name(type_key)}_{safe_name(stage)}.html"
        file_path.write_bytes(resp.content)

        record = PageRecord(
            type_key=type_key,
            stage=stage,
            url=str(resp.request.url),
            method=method.upper(),
            status_code=resp.status_code,
            final_url=resp.url,
            encoding=resp.encoding or "",
            elapsed_ms=getattr(resp, "elapsed_ms", 0),
            content_length=len(resp.content),
            file=rel(file_path, self.run_dir),
            request_data=request_data,
        )
        meta_file = self.meta_dir / f"{safe_name(type_key)}_{safe_name(stage)}.json"
        meta_file.write_text(json.dumps(asdict(record), ensure_ascii=False, indent=2), encoding="utf-8")

        self.conn.execute(
            """
            INSERT INTO raw_pages
            (run_id, type_key, stage, url, method, status_code, final_url, encoding,
             elapsed_ms, content_length, file, request_data_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                self.run_id,
                record.type_key,
                record.stage,
                record.url,
                record.method,
                record.status_code,
                record.final_url,
                record.encoding,
                record.elapsed_ms,
                record.content_length,
                record.file,
                json.dumps(request_data, ensure_ascii=False) if request_data else None,
            ),
        )
        return file_path.read_text(encoding=resp.encoding or "utf-8", errors="replace"), record.file

    def save_forms(self, type_key: str, stage: str, forms: list[dict[str, Any]]) -> None:
        path = self.meta_dir / f"{safe_name(type_key)}_{safe_name(stage)}_forms.json"
        path.write_text(json.dumps(forms, ensure_ascii=False, indent=2), encoding="utf-8")
        for form_index, form in enumerate(forms):
            self.conn.execute(
                """
                INSERT INTO forms
                (run_id, type_key, stage, form_index, action, method, fields_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    self.run_id,
                    type_key,
                    stage,
                    form_index,
                    form.get("action", ""),
                    form.get("method", ""),
                    json.dumps(form.get("fields", []), ensure_ascii=False),
                ),
            )

    def save_tables(self, type_key: str, stage: str, tables: list[dict[str, Any]]) -> None:
        path = self.tables_dir / f"{safe_name(type_key)}_{safe_name(stage)}_tables.json"
        path.write_text(json.dumps(tables, ensure_ascii=False, indent=2), encoding="utf-8")
        for table in tables:
            for row in table["rows"]:
                for cell in row["cells"]:
                    self.conn.execute(
                        """
                        INSERT INTO table_cells
                        (run_id, type_key, stage, table_index, row_index, col_index,
                         tag, rowspan, colspan, attrs_json, text, lines_json, raw_html)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        (
                            self.run_id,
                            type_key,
                            stage,
                            table["table_index"],
                            row["row_index"],
                            cell["col_index"],
                            cell["tag"],
                            cell["rowspan"],
                            cell["colspan"],
                            json.dumps(cell["attrs"], ensure_ascii=False),
                            cell["text"],
                            json.dumps(cell["lines"], ensure_ascii=False),
                            cell["raw_html"],
                        ),
                    )

    def save_courses(self, courses: list[CourseRecord]) -> None:
        if not courses:
            return
        jsonl_path = self.parsed_dir / "global_courses.jsonl"
        csv_path = self.parsed_dir / "global_courses.csv"

        with jsonl_path.open("a", encoding="utf-8") as f:
            for course in courses:
                f.write(json.dumps(asdict(course), ensure_ascii=False) + "\n")

        write_header = not csv_path.exists()
        with csv_path.open("a", newline="", encoding="utf-8-sig") as f:
            writer = csv.DictWriter(f, fieldnames=list(asdict(courses[0]).keys()))
            if write_header:
                writer.writeheader()
            for course in courses:
                writer.writerow(asdict(course))

        self.conn.executemany(
            """
            INSERT OR REPLACE INTO course_records
            (id, run_id, type_key, type_code, type_label, term, entity_name,
             course_name, teacher, room, class_name, weeks_text, day, sections_text,
             section_numbers, slot_index, table_index, row_index, col_index,
             raw_text, raw_html, raw_lines_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    c.id,
                    c.run_id,
                    c.type_key,
                    c.type_code,
                    c.type_label,
                    c.term,
                    c.entity_name,
                    c.course_name,
                    c.teacher,
                    c.room,
                    c.class_name,
                    c.weeks_text,
                    c.day,
                    c.sections_text,
                    c.section_numbers,
                    c.slot_index,
                    c.table_index,
                    c.row_index,
                    c.col_index,
                    c.raw_text,
                    c.raw_html,
                    c.raw_lines_json,
                )
                for c in courses
            ],
        )

    def write_summary(self, summary: dict[str, Any]) -> None:
        path = self.run_dir / "summary.json"
        path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
        self.conn.execute(
            "INSERT OR REPLACE INTO crawl_runs (run_id, started_at, summary_json) VALUES (?, ?, ?)",
            (self.run_id, summary.get("started_at", ""), json.dumps(summary, ensure_ascii=False)),
        )

    def _init_db(self) -> None:
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS crawl_runs (
                run_id TEXT PRIMARY KEY,
                started_at TEXT,
                summary_json TEXT
            );

            CREATE TABLE IF NOT EXISTS raw_pages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT,
                type_key TEXT,
                stage TEXT,
                url TEXT,
                method TEXT,
                status_code INTEGER,
                final_url TEXT,
                encoding TEXT,
                elapsed_ms INTEGER,
                content_length INTEGER,
                file TEXT,
                request_data_json TEXT
            );

            CREATE TABLE IF NOT EXISTS forms (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT,
                type_key TEXT,
                stage TEXT,
                form_index INTEGER,
                action TEXT,
                method TEXT,
                fields_json TEXT
            );

            CREATE TABLE IF NOT EXISTS table_cells (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT,
                type_key TEXT,
                stage TEXT,
                table_index INTEGER,
                row_index INTEGER,
                col_index INTEGER,
                tag TEXT,
                rowspan INTEGER,
                colspan INTEGER,
                attrs_json TEXT,
                text TEXT,
                lines_json TEXT,
                raw_html TEXT
            );

            CREATE TABLE IF NOT EXISTS course_records (
                id TEXT PRIMARY KEY,
                run_id TEXT,
                type_key TEXT,
                type_code TEXT,
                type_label TEXT,
                term TEXT,
                entity_name TEXT,
                course_name TEXT,
                teacher TEXT,
                room TEXT,
                class_name TEXT,
                weeks_text TEXT,
                day INTEGER,
                sections_text TEXT,
                section_numbers TEXT,
                slot_index INTEGER,
                table_index INTEGER,
                row_index INTEGER,
                col_index INTEGER,
                raw_text TEXT,
                raw_html TEXT,
                raw_lines_json TEXT
            );
            """
        )


def extract_forms(html: str) -> list[dict[str, Any]]:
    soup = BeautifulSoup(html, "html.parser")
    forms: list[dict[str, Any]] = []
    for form in soup.select("form"):
        fields: list[dict[str, Any]] = []
        for field in form.select("input, select, textarea"):
            name = field.get("name", "")
            field_info: dict[str, Any] = {
                "tag": field.name,
                "name": name,
                "id": field.get("id", ""),
                "type": field.get("type", ""),
                "value": field.get("value", ""),
                "text": clean_text(field.get_text(" ")),
                "attrs": dict(field.attrs),
            }
            if field.name == "select":
                options = []
                for option in field.select("option"):
                    options.append(
                        {
                            "value": option.get("value", ""),
                            "text": clean_text(option.get_text(" ")),
                            "selected": option.has_attr("selected"),
                        }
                    )
                field_info["options"] = options
                selected = next((o for o in options if o["selected"]), None)
                field_info["selected_value"] = selected["value"] if selected else ""
            fields.append(field_info)

        forms.append(
            {
                "action": form.get("action", ""),
                "method": form.get("method", "GET").upper(),
                "attrs": dict(form.attrs),
                "fields": fields,
            }
        )
    return forms


def get_form_defaults(html: str, include_selects: bool) -> dict[str, str]:
    soup = BeautifulSoup(html, "html.parser")
    defaults: dict[str, str] = {}
    for tag in soup.select("input[name], textarea[name]"):
        name = tag.get("name", "")
        if name:
            defaults[name] = tag.get("value", "")

    if include_selects:
        for select in soup.select("select[name]"):
            name = select.get("name", "")
            if not name:
                continue
            options = select.select("option")
            selected = next((o for o in options if o.has_attr("selected")), None)
            blank = next((o for o in options if o.get("value", "") == ""), None)
            chosen = selected or blank or (options[0] if options else None)
            defaults[name] = chosen.get("value", "") if chosen else ""

    return defaults


def extract_tables(html: str) -> list[dict[str, Any]]:
    soup = BeautifulSoup(html, "html.parser")
    tables: list[dict[str, Any]] = []
    for table_index, table in enumerate(soup.select("table")):
        table_data = {
            "table_index": table_index,
            "id": table.get("id", ""),
            "class": table.get("class", []),
            "attrs": dict(table.attrs),
            "caption": clean_text(table.select_one("caption").get_text(" ")) if table.select_one("caption") else "",
            "rows": [],
        }
        for row_index, tr in enumerate(table.select("tr")):
            row_data = {"row_index": row_index, "attrs": dict(tr.attrs), "text": clean_text(tr.get_text(" ")), "cells": []}
            for col_index, cell in enumerate(tr.find_all(["th", "td"], recursive=False)):
                lines = html_to_lines(cell.decode_contents())
                row_data["cells"].append(
                    {
                        "col_index": col_index,
                        "tag": cell.name,
                        "rowspan": int(cell.get("rowspan", "1") or "1"),
                        "colspan": int(cell.get("colspan", "1") or "1"),
                        "attrs": dict(cell.attrs),
                        "text": clean_text(cell.get_text(" ")),
                        "lines": lines,
                        "raw_html": cell.decode_contents(),
                    }
                )
            table_data["rows"].append(row_data)
        tables.append(table_data)
    return tables


def html_to_lines(inner_html: str) -> list[str]:
    parts = re.split(r"<br\s*/?>", inner_html, flags=re.IGNORECASE)
    lines = [clean_text(BeautifulSoup(part, "html.parser").get_text(" ")) for part in parts]
    return [line for line in lines if line]


def table_grid_rows(table: Tag) -> list[list[Tag | None]]:
    grid: list[list[Tag | None]] = []
    spans: dict[tuple[int, int], Tag] = {}

    for r_idx, tr in enumerate(table.select("tr")):
        row: list[Tag | None] = []
        c_idx = 0
        for cell in tr.find_all(["th", "td"], recursive=False):
            while (r_idx, c_idx) in spans:
                row.append(spans[(r_idx, c_idx)])
                c_idx += 1

            rowspan = int(cell.get("rowspan", "1") or "1")
            colspan = int(cell.get("colspan", "1") or "1")
            for offset in range(colspan):
                row.append(cell)
                if rowspan > 1:
                    for r_offset in range(1, rowspan):
                        spans[(r_idx + r_offset, c_idx + offset)] = cell
            c_idx += colspan

        while (r_idx, c_idx) in spans:
            row.append(spans[(r_idx, c_idx)])
            c_idx += 1
        grid.append(row)
    return grid


def parse_global_courses(html: str, type_key: str, run_id: str, term: str) -> list[CourseRecord]:
    config = SCHEDULE_TYPES[type_key]
    soup = BeautifulSoup(html, "html.parser")
    table = soup.select_one("table#timetable")
    if not table:
        return []

    all_tables = soup.select("table")
    table_index = all_tables.index(table) if table in all_tables else 0
    grid = table_grid_rows(table)
    records: list[CourseRecord] = []

    for row_index, row in enumerate(grid[2:], start=2):
        if not row:
            continue
        entity_cell = row[0]
        entity_name = clean_text(entity_cell.get_text(" ")) if isinstance(entity_cell, Tag) else ""
        if not entity_name:
            continue

        for col_index, cell in enumerate(row[1:], start=1):
            if not isinstance(cell, Tag):
                continue
            day = ((col_index - 1) // 5) + 1
            slot_index = (col_index - 1) % 5
            if day < 1 or day > 7:
                continue

            for block_index, div in enumerate(cell.select("div.kbcontent1, div.kbcontent")):
                for hidden in div.select("font.kchConfig"):
                    hidden.decompose()
                raw_html = div.decode_contents()
                raw_text = clean_text(div.get_text(" "))
                if not raw_text:
                    continue

                blocks = split_course_blocks(raw_html)
                for sub_index, block_html in enumerate(blocks):
                    lines = html_to_lines(block_html)
                    if not lines:
                        continue
                    parsed = parse_course_lines(type_key, entity_name, lines)
                    if not parsed["course_name"]:
                        continue

                    section_numbers = SECTION_SLOTS.get(slot_index, "")
                    section_text = f"第{section_numbers}节" if section_numbers else ""
                    raw_lines_json = json.dumps(lines, ensure_ascii=False)
                    record_id = make_course_id(
                        run_id,
                        type_key,
                        entity_name,
                        parsed["course_name"],
                        day,
                        section_numbers,
                        parsed["weeks_text"],
                        row_index,
                        col_index,
                        block_index,
                        sub_index,
                    )
                    records.append(
                        CourseRecord(
                            id=record_id,
                            run_id=run_id,
                            type_key=type_key,
                            type_code=config["code"],
                            type_label=config["label"],
                            term=term,
                            entity_name=entity_name,
                            course_name=parsed["course_name"],
                            teacher=parsed["teacher"],
                            room=parsed["room"],
                            class_name=parsed["class_name"],
                            weeks_text=parsed["weeks_text"],
                            day=day,
                            sections_text=section_text,
                            section_numbers=section_numbers,
                            slot_index=slot_index,
                            table_index=table_index,
                            row_index=row_index,
                            col_index=col_index,
                            raw_text=raw_text,
                            raw_html=block_html,
                            raw_lines_json=raw_lines_json,
                        )
                    )

    return dedupe_courses(records)


def split_course_blocks(raw_html: str) -> list[str]:
    blocks = re.split(r"(?:-{5,}|<hr\s*/?>)", raw_html, flags=re.IGNORECASE)
    cleaned = [block.strip() for block in blocks if clean_text(BeautifulSoup(block, "html.parser").get_text(" "))]
    return cleaned or [raw_html]


def parse_course_lines(type_key: str, entity_name: str, lines: list[str]) -> dict[str, str]:
    result = {
        "course_name": "",
        "teacher": "",
        "room": "",
        "class_name": "",
        "weeks_text": "",
    }

    if type_key == "teacher":
        result["teacher"] = entity_name
        result["course_name"] = value_at(lines, 0)
        result["class_name"] = value_at(lines, 1)
        result["weeks_text"] = normalize_weeks(value_at(lines, 2))
        result["room"] = value_at(lines, 3)
    elif type_key == "classroom":
        result["room"] = entity_name
        result["course_name"] = value_at(lines, 0)
        result["teacher"] = value_at(lines, 1)
        result["class_name"] = value_at(lines, 2)
        result["weeks_text"] = normalize_weeks(value_at(lines, 3))
    elif type_key == "course":
        result["course_name"] = entity_name
        data_lines = lines[1:] if lines and lines[0] == entity_name else lines
        result["class_name"] = value_at(data_lines, 0)
        result["teacher"] = value_at(data_lines, 1)
        result["weeks_text"] = normalize_weeks(value_at(data_lines, 2))
        result["room"] = value_at(data_lines, 3)
    else:
        result["class_name"] = entity_name
        result["course_name"] = value_at(lines, 0)
        teacher_or_weeks = value_at(lines, 1)
        teacher, weeks = split_teacher_weeks(teacher_or_weeks)
        result["teacher"] = teacher
        result["weeks_text"] = normalize_weeks(weeks or value_at(lines, 2))
        result["room"] = value_at(lines, 3)
        if not result["room"] and len(lines) == 3 and not looks_like_weeks(lines[2]):
            result["room"] = lines[2]

    if not result["weeks_text"]:
        for line in lines:
            if looks_like_weeks(line):
                result["weeks_text"] = normalize_weeks(line)
                break

    return {key: clean_text(value) for key, value in result.items()}


def value_at(values: list[str], index: int) -> str:
    return values[index] if index < len(values) else ""


def looks_like_weeks(text: str) -> bool:
    return bool(re.search(r"\d+\s*(?:-|,|周|週|week|Week|\()", text))


def normalize_weeks(text: str) -> str:
    text = clean_text(text)
    match = re.search(r"\(([^)]*周[^)]*)\)", text)
    if match:
        return match.group(1).strip()
    return text.strip("() ")


def split_teacher_weeks(text: str) -> tuple[str, str]:
    text = clean_text(text)
    match = re.search(r"^(.*?)\s*\(([^)]*周[^)]*)\)\s*$", text)
    if match:
        return match.group(1).strip(), match.group(2).strip()
    if looks_like_weeks(text):
        return "", normalize_weeks(text)
    return text, ""


def make_course_id(*parts: Any) -> str:
    raw = "|".join(str(part) for part in parts)
    return hashlib.sha1(raw.encode("utf-8")).hexdigest()


def dedupe_courses(records: list[CourseRecord]) -> list[CourseRecord]:
    seen: set[tuple[str, ...]] = set()
    result: list[CourseRecord] = []
    for record in records:
        key = (
            record.type_key,
            record.entity_name,
            record.course_name,
            record.teacher,
            record.room,
            record.class_name,
            record.weeks_text,
            str(record.day),
            record.section_numbers,
            record.raw_lines_json,
        )
        if key in seen:
            continue
        seen.add(key)
        result.append(record)
    return result


def selected_term_from_home(html: str) -> str:
    soup = BeautifulSoup(html, "html.parser")
    selected = soup.select_one("select[name=xnxqh] option[selected]")
    if selected and selected.get("value"):
        return selected.get("value", "")
    field = soup.select_one("input[name=xnxqh]")
    return field.get("value", "") if field else ""


def crawl_type(
    crawler: NjfuFullCrawler,
    storage: CrawlStorage,
    type_key: str,
    term: str,
    keyword: str,
    include_default_selects: bool,
    extra_params: dict[str, str],
) -> dict[str, Any]:
    config = SCHEDULE_TYPES[type_key]
    home_url = f"{JWXT_BASE}/{config['home_path']}"
    data_url = f"{JWXT_BASE}/{config['data_path']}"

    home_resp = crawler.fetch(home_url)
    home_html, _ = storage.save_response(type_key, "home", "GET", home_resp)
    storage.save_forms(type_key, "home", extract_forms(home_html))
    storage.save_tables(type_key, "home", extract_tables(home_html))

    form_defaults = get_form_defaults(home_html, include_selects=include_default_selects)
    target_term = term or selected_term_from_home(home_html) or form_defaults.get("xnxqh", "")
    kbjcmsid = form_defaults.get("kbjcmsid", "933E103D1CA84D64A71CE6FC60BFE57B")

    data = {}
    if include_default_selects:
        data.update(form_defaults)
    data.update({"xnxqh": target_term, "kbjcmsid": kbjcmsid, config["keyword_param"]: keyword})
    data.update({key: value for key, value in extra_params.items() if value != ""})

    data_resp = crawler.fetch(data_url, method="POST", data=data)
    data_html, _ = storage.save_response(type_key, "data", "POST", data_resp, request_data=data)
    storage.save_forms(type_key, "data", extract_forms(data_html))
    tables = extract_tables(data_html)
    storage.save_tables(type_key, "data", tables)

    courses = parse_global_courses(data_html, type_key, storage.run_id, target_term)
    storage.save_courses(courses)

    return {
        "type_key": type_key,
        "type_code": config["code"],
        "type_label": config["label"],
        "term": target_term,
        "home_url": home_url,
        "data_url": data_url,
        "request_data": data,
        "table_count": len(tables),
        "table_cell_count": sum(len(row["cells"]) for table in tables for row in table["rows"]),
        "course_record_count": len(courses),
    }


def parse_key_values(values: list[str]) -> dict[str, str]:
    result: dict[str, str] = {}
    for item in values:
        if "=" not in item:
            raise ValueError(f"参数格式错误: {item}，应为 key=value")
        key, value = item.split("=", 1)
        result[key] = value
    return result


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="NJFU 全校课表全量爬虫")
    parser.add_argument("--student-id", default=os.getenv("NJFU_STUDENT_ID", ""), help="学号，默认读取 NJFU_STUDENT_ID")
    parser.add_argument("--password", default=os.getenv("NJFU_PASSWORD", ""), help="密码，默认读取 NJFU_PASSWORD")
    parser.add_argument("--term", default="", help="学年学期，例如 2025-2026-2；留空使用页面默认选中学期")
    parser.add_argument(
        "--types",
        nargs="+",
        default=["teacher", "classroom", "class", "course"],
        choices=list(SCHEDULE_TYPES.keys()) + ["all"],
        help="要抓取的课表类型，默认全部",
    )
    parser.add_argument("--keyword", default="", help="查询关键字；默认空字符串表示尽量抓取全部")
    parser.add_argument("--out", default="data/njfu_crawl", help="输出目录")
    parser.add_argument("--timeout", type=int, default=30, help="单个请求超时时间，秒")
    parser.add_argument("--verify-ssl", action="store_true", help="启用 SSL 证书校验；默认关闭以兼容学校站点")
    parser.add_argument(
        "--include-default-selects",
        action="store_true",
        help="提交首页所有下拉框默认值。默认不提交，避免 skyx/xq/jxl 等默认值缩小全量范围",
    )
    parser.add_argument(
        "--param",
        action="append",
        default=[],
        help="额外 POST 参数，可重复传入，例如 --param skyx=01 --param xq=1",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    if not args.student_id or not args.password:
        print("缺少账号或密码。请传 --student-id/--password，或设置 NJFU_STUDENT_ID/NJFU_PASSWORD。", file=sys.stderr)
        return 2

    type_keys = list(SCHEDULE_TYPES.keys()) if "all" in args.types else args.types
    extra_params = parse_key_values(args.param)
    run_id = datetime.now().strftime("%Y%m%d_%H%M%S")
    storage = CrawlStorage(Path(args.out), run_id)

    summary: dict[str, Any] = {
        "run_id": run_id,
        "started_at": datetime.now().isoformat(timespec="seconds"),
        "student_id": args.student_id,
        "term_arg": args.term,
        "types": type_keys,
        "output_dir": str(storage.run_dir),
        "results": [],
        "errors": [],
    }

    try:
        crawler = NjfuFullCrawler(args.student_id, args.password, verify_ssl=args.verify_ssl, timeout=args.timeout)
        print("正在登录 NJFU 统一认证...")
        crawler.login()
        print("登录成功，开始抓取全校课表。")

        for type_key in type_keys:
            label = SCHEDULE_TYPES[type_key]["label"]
            print(f"抓取 {label} ({type_key}) ...")
            try:
                result = crawl_type(
                    crawler=crawler,
                    storage=storage,
                    type_key=type_key,
                    term=args.term,
                    keyword=args.keyword,
                    include_default_selects=args.include_default_selects,
                    extra_params=extra_params,
                )
                summary["results"].append(result)
                print(
                    f"完成 {label}: 表格 {result['table_count']} 个，"
                    f"单元格 {result['table_cell_count']} 个，课程记录 {result['course_record_count']} 条。"
                )
            except Exception as exc:  # noqa: BLE001 - keep crawling other types.
                message = f"{label} 抓取失败: {exc}"
                summary["errors"].append({"type_key": type_key, "message": str(exc)})
                print(message, file=sys.stderr)

        summary["finished_at"] = datetime.now().isoformat(timespec="seconds")
        storage.write_summary(summary)
        print(f"输出目录: {storage.run_dir}")
        print(f"SQLite: {storage.db_path}")
        return 1 if summary["errors"] else 0
    finally:
        storage.close()


if __name__ == "__main__":
    raise SystemExit(main())
