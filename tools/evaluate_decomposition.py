
#!/usr/bin/env python3
"""Evaluate the current goal-decomposition prompt against representative goals.

The harness deliberately stores only aggregate counts. It never logs request
headers, API keys, response bodies, or individual model results.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Sequence


ROOT_DIR = Path(__file__).resolve().parents[1]
PROMPTS_PATH = (
    ROOT_DIR
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "lifementor"
    / "app"
    / "llm"
    / "Prompts.kt"
)
REPORT_PATH = ROOT_DIR / "docs" / "eval.md"
DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
DEFAULT_MODEL = "glm-4-flash"
DEFAULT_REQUESTS_PER_CASE = 20
DEFAULT_TIMEOUT_SECONDS = 240
DEFAULT_WORKERS = 1


@dataclass(frozen=True)
class GoalCase:
    title: str
    domain_label: str
    role: str
    stage: str
    vision: str
    profile: dict[str, object]
    context: str


GOAL_CASES: tuple[GoalCase, ...] = (
    GoalCase(
        title="两个月内收到一份 AI 产品助理岗位的面试邀请",
        domain_label="求职",
        role="👩‍🎓 学生",
        stage="应届求职",
        vision="进入重视用户研究和协作的 AI 产品团队",
        profile={
            "专业": "信息管理",
            "现有经历": "一段产品实习",
            "每周可投入小时": 12,
            "通勤约束": "每次面试往返不超过 2 小时",
            "作品集": "只有题目清单，尚无案例",
        },
        context="目标岗位在本地有约 15 个；平日晚上 8 点后和周末可投入时间。",
    ),
    GoalCase(
        title="在 18 个月内完成研究生入学考试备考与目标院校申请",
        domain_label="升学",
        role="📚 学生",
        stage="长期备考",
        vision="进入适合自己的研究生项目继续深造",
        profile={
            "考试科目": "数学、英语、专业课",
            "薄弱科目": "数学",
            "每周可投入小时": 18,
            "学习地点": "住所附近图书馆，周末开放 8 小时",
        },
        context="距离考试约 18 个月，已收集教材；工作日每天最多学习 2 小时。",
    ),
    GoalCase(
        title="用一年时间从银行柜员转型为数据分析师",
        domain_label="职业转型",
        role="💼 在职人员",
        stage="在职转型",
        vision="用数据技能获得更有成长空间的工作",
        profile={
            "工作年限": 4,
            "已有技能": "Excel、客户沟通",
            "每周可投入小时": 7,
            "学习预算": 3000,
            "地理约束": "暂时不能离开常住城市",
        },
        context="必须保留现有收入；工作日每天可学习 1 小时，周末可投入 4 小时。",
    ),
    GoalCase(
        title="三个月内建立家庭预算记录并完成个人理财基础学习",
        domain_label="预算与财务教育",
        role="🏠 成年人",
        stage="理财入门",
        vision="清楚掌握收支并形成可持续的储蓄习惯",
        profile={
            "记录频率": "希望每周复盘一次",
            "学习预算": 200,
            "学习时段": "工作日午休 20 分钟",
            "现有工具": "电子表格",
        },
        context="目前没有完整记录支出；希望先学习预算、应急金和风险等基础概念。",
    ),
    GoalCase(
        title="用六个月把英语写作从 5.5 分提升到目标 6.5 分",
        domain_label="语言学习",
        role="🗣️ 学生",
        stage="考试准备",
        vision="满足交换项目的语言要求",
        profile={
            "当前写作分数": 5.5,
            "目标写作分数": 6.5,
            "每周可投入小时": 5,
            "练习条件": "没有固定口语搭子",
            "地理约束": "只在线上参加课程",
        },
        context="每天通勤可以听 30 分钟材料，周末能完成一次完整写作练习。",
    ),
    GoalCase(
        title="四个月内通过 PMP 认证考试",
        domain_label="职业认证",
        role="📋 项目人员",
        stage="资格备考",
        vision="系统补足项目管理知识并拓展职业选择",
        profile={
            "项目经验": "三年",
            "学习偏好": "短时练习和错题复盘",
            "每周可投入小时": 7,
            "考试预算": 1200,
            "工作约束": "每月可能出差 3 天",
        },
        context="已购买一套在线课程，但还没有固定学习计划；工作日每天最多学习一小时。",
    ),
    GoalCase(
        title="十周内完成一次 20 公里城市骑行活动",
        domain_label="健康与运动",
        role="🚴 成年人",
        stage="运动入门",
        vision="养成能长期坚持的周末运动习惯",
        profile={
            "当前活动量": "偶尔散步，不常骑车",
            "训练频率": "每周三次",
            "器材预算": 500,
            "天气约束": "雨天只能室内训练",
        },
        context="工作日下班后有空；没有室内骑行台，计划从低强度开始。",
    ),
    GoalCase(
        title="六个月内用离线环境完成一个可展示的 Python 数据分析项目",
        domain_label="技能学习",
        role="💻 初学者",
        stage="建立基础",
        vision="具备解决真实业务问题的编程能力",
        profile={
            "编程基础": "无",
            "已有工具": "电子表格和本地开发环境",
            "每周可投入小时": 4,
            "网络约束": "大部分时间只能离线学习",
        },
        context="已经下载公开数据和课程材料，希望每晚用 30 分钟完成项目。",
    ),
    GoalCase(
        title="三个月内用三千元预算验证一个本地手作网店副业",
        domain_label="副业与创业",
        role="🧵 创作者",
        stage="想法验证",
        vision="小成本验证产品是否有人愿意购买",
        profile={
            "产品想法": "手作布艺",
            "启动预算": 3000,
            "每周可投入小时": 5,
            "目标市场": "常住城市周末市集",
            "地理约束": "只能参加本地活动",
        },
        context="已有几个样品，但还没有测试定价、摆摊需求或接触潜在客户。",
    ),
    GoalCase(
        title="用两个月建立适合夜班工作的规律睡眠安排",
        domain_label="生活习惯",
        role="🌙 轮班人员",
        stage="习惯调整",
        vision="保持可持续的工作和休息节奏",
        profile={
            "工作安排": "每四周轮换一次夜班",
            "调整幅度": "每次最多提前 20 分钟",
            "可用设备": "普通闹钟",
            "环境约束": "住处白天有交通噪音",
        },
        context="目前休息时段不固定；希望通过小步骤增加睡眠安排的稳定性。",
    ),
)

class InvalidModelResponse(Exception):
    """A reply was not a single JSON value."""

    def __init__(self) -> None:
        super().__init__("model response was not valid JSON")


class TransportError(Exception):
    """A request or OpenAI-compatible response envelope failed."""

    def __init__(self) -> None:
        super().__init__("request failed")


class NonEchoingArgumentParser(argparse.ArgumentParser):
    def error(self, message: str) -> None:
        self.print_usage(sys.stderr)
        self.exit(2, "error: invalid command-line arguments\n")


@dataclass(frozen=True)
class EvaluationResult:
    case_count: int
    requests_per_case: int
    valid_responses: int
    invalid_responses: int
    transport_errors: int
    timestamp_utc: str
    workers: int = 1

    @property
    def attempts(self) -> int:
        return self.case_count * self.requests_per_case


_KOTLIN_STRING_CONSTANT = re.compile(
    r'^\s*(?:(?:private|internal|public)\s+)?const\s+val\s+([A-Z][A-Z0-9_]*)\s*=\s*"((?:\\.|[^"\\])*)"\s*$',
    flags=re.MULTILINE,
)
_KOTLIN_INTERPOLATION = re.compile(r"\$(?:\{([A-Z][A-Z0-9_]*)\}|([A-Z][A-Z0-9_]*))")


def _resolve_kotlin_interpolations(prompt: str, source: str) -> str:
    constants = {
        match.group(1): json.loads(f'"{match.group(2)}"')
        for match in _KOTLIN_STRING_CONSTANT.finditer(source)
    }
    resolved = prompt
    for _ in range(len(constants) + 1):
        expanded = _KOTLIN_INTERPOLATION.sub(
            lambda match: constants.get(match.group(1) or match.group(2), match.group(0)),
            resolved,
        )
        if expanded == resolved:
            break
        resolved = expanded
    if _KOTLIN_INTERPOLATION.search(resolved):
        raise ValueError("DECOMPOSE_SYSTEM contains an unresolved Kotlin interpolation")
    return resolved


def extract_system_prompt(prompts_path: Path | str) -> str:
    """Read the evaluated Kotlin DECOMPOSE_SYSTEM triple-quoted constant."""
    source = Path(prompts_path).read_text(encoding="utf-8")
    match = re.search(
        r"const\s+val\s+DECOMPOSE_SYSTEM\s*=\s*\"\"\"(.*?)\"\"\"",
        source,
        flags=re.DOTALL,
    )
    if match is None:
        raise ValueError("DECOMPOSE_SYSTEM prompt was not found")
    return _resolve_kotlin_interpolations(match.group(1), source)


def _reject_nonstandard_json_constant(_value: str) -> None:
    raise ValueError("non-standard JSON constant")


def parse_model_response(raw_response: str) -> object:
    """Parse a plain or JSON-fenced response without echoing its contents."""
    if not isinstance(raw_response, str):
        raise InvalidModelResponse() from None

    candidate = raw_response.strip()
    if candidate.startswith("```"):
        match = re.fullmatch(
            r"```(?:json)?[ \t]*\r?\n(.*?)\r?\n?```",
            candidate,
            flags=re.IGNORECASE | re.DOTALL,
        )
        if match is None:
            raise InvalidModelResponse() from None
        candidate = match.group(1).strip()

    try:
        return json.loads(candidate, parse_constant=_reject_nonstandard_json_constant)
    except (ValueError, TypeError):
        raise InvalidModelResponse() from None


def _is_nonblank_string(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _english_word_count(value: str) -> int:
    return len(re.findall(r"[A-Za-z]+", value))


def _is_english_text(value: object) -> bool:
    if not _is_nonblank_string(value):
        return False
    text = value.strip()
    return (
        all(0x20 <= ord(character) <= 0x7E for character in text)
        and any(character.isascii() and character.isalpha() for character in text)
    )


def _has_milestone_title_format(value: object) -> bool:
    if not _is_english_text(value):
        return False
    return bool(re.match(r"^Q[1-4]\s*\|\s*\S.*$", value))


def _has_week_title_format(value: object) -> bool:
    if not _is_english_text(value):
        return False
    return bool(re.match(r"^Week\s+[1-9]\d*\s*\|\s*\S.*$", value))


def is_valid_decomposition(value: object) -> bool:
    """Validate the hierarchy limits required by the app's task tree."""
    if not isinstance(value, dict) or set(value) != {"milestones"}:
        return False

    milestones = value.get("milestones")
    if not isinstance(milestones, list) or not 2 <= len(milestones) <= 4:
        return False

    for milestone in milestones:
        if (
            not isinstance(milestone, dict)
            or set(milestone) != {"title", "weeks"}
            or not _has_milestone_title_format(milestone.get("title"))
        ):
            return False

        weeks = milestone.get("weeks")
        if not isinstance(weeks, list) or not 2 <= len(weeks) <= 4:
            return False

        for week in weeks:
            if (
                not isinstance(week, dict)
                or set(week) != {"title", "days"}
                or not _has_week_title_format(week.get("title"))
            ):
                return False

            days = week.get("days")
            if not isinstance(days, list) or not 1 <= len(days) <= 3:
                return False

            for action in days:
                if not _is_english_text(action) or _english_word_count(action) > 15:
                    return False

    return True


def build_user_message(case: GoalCase) -> str:
    """Build the goal input in the same field order used by the app."""
    profile_json = json.dumps(case.profile, ensure_ascii=False, separators=(",", ":"))
    return (
        f"目标：{case.title}\n"
        f"所属领域：{case.domain_label}\n"
        f"角色：{case.role}，阶段：{case.stage}；愿景：{case.vision}\n"
        f"画像：{profile_json}\n"
        f"近期状态：{case.context}\n"
        "请按规范输出 JSON。"
    )


def _validate_base_url(base_url: str) -> str:
    try:
        parsed = urllib.parse.urlsplit(base_url)
        if (
            parsed.scheme not in {"http", "https"}
            or not parsed.netloc
            or parsed.username is not None
            or parsed.password is not None
            or parsed.query
            or parsed.fragment
        ):
            raise TransportError() from None
    except (TypeError, ValueError):
        raise TransportError() from None
    return base_url.rstrip("/") + "/chat/completions"


def request_chat_completion(
    base_url: str,
    model: str,
    api_key: str,
    system_prompt: str,
    user_message: str,
    timeout: int = DEFAULT_TIMEOUT_SECONDS,
) -> str:
    """Call an OpenAI-compatible chat-completions endpoint.

    Network and envelope errors intentionally discard provider details so
    exceptions can never leak response text or authorization headers.
    """
    endpoint = _validate_base_url(base_url)
    request_body = json.dumps(
        {
            "model": model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_message},
            ],
            "temperature": 0.7,
        },
        ensure_ascii=False,
    ).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=request_body,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            envelope = json.load(response)
        content = envelope["choices"][0]["message"]["content"]
    except (
        urllib.error.URLError,
        TimeoutError,
        OSError,
        ValueError,
        KeyError,
        IndexError,
        TypeError,
    ):
        raise TransportError() from None

    if not isinstance(content, str):
        raise TransportError() from None
    return content


RequestFunction = Callable[[str, str, str, str, str, int], str]


def run_evaluation(
    api_key: str,
    base_url: str = DEFAULT_BASE_URL,
    model: str = DEFAULT_MODEL,
    requests_per_case: int = DEFAULT_REQUESTS_PER_CASE,
    timeout: int = DEFAULT_TIMEOUT_SECONDS,
    workers: int = DEFAULT_WORKERS,
    prompt_path: Path | str = PROMPTS_PATH,
    cases: Sequence[GoalCase] = GOAL_CASES,
    request_function: RequestFunction | None = None,
) -> EvaluationResult:
    """Run each goal case sequentially and return aggregate counts only."""
    if requests_per_case < 1:
        raise ValueError("requests per case must be positive")
    if not cases:
        raise ValueError("at least one goal case is required")

    system_prompt = extract_system_prompt(prompt_path)
    if workers < 1:
        raise ValueError("workers must be positive")

    request_one = request_function or request_chat_completion
    messages = [
        build_user_message(case)
        for case in cases
        for _ in range(requests_per_case)
    ]
    valid_responses = 0
    invalid_responses = 0
    transport_errors = 0

    def request_message(user_message: str) -> str:
        return request_one(
            base_url,
            model,
            api_key,
            system_prompt,
            user_message,
            timeout,
        )

    def record_response(raw_response: str) -> None:
        nonlocal valid_responses, invalid_responses
        try:
            parsed_response = parse_model_response(raw_response)
        except InvalidModelResponse:
            invalid_responses += 1
            return

        if is_valid_decomposition(parsed_response):
            valid_responses += 1
        else:
            invalid_responses += 1

    if workers == 1:
        for user_message in messages:
            try:
                raw_response = request_message(user_message)
            except TransportError:
                transport_errors += 1
                continue
            record_response(raw_response)
    else:
        with ThreadPoolExecutor(max_workers=workers) as executor:
            futures = [executor.submit(request_message, message) for message in messages]
            for future in as_completed(futures):
                try:
                    raw_response = future.result()
                except TransportError:
                    transport_errors += 1
                    continue
                record_response(raw_response)

    return EvaluationResult(
        case_count=len(cases),
        requests_per_case=requests_per_case,
        valid_responses=valid_responses,
        invalid_responses=invalid_responses,
        transport_errors=transport_errors,
        timestamp_utc=datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        workers=workers,
    )


def format_summary(result: EvaluationResult) -> str:
    """Format aggregate-only counts; model replies and credentials are absent."""
    received_responses = result.valid_responses + result.invalid_responses
    valid_response_rate = (
        f"{result.valid_responses}/{received_responses} "
        f"({100 * result.valid_responses / received_responses:.1f}%)"
        if received_responses
        else "0/0 (N/A; no model responses received)"
    )
    return (
        f"### Evaluation run — {result.timestamp_utc}\n\n"
        f"- Goal cases: {result.case_count}\n"
        f"- Requests per goal: {result.requests_per_case}\n"
        f"- Total requests: {result.attempts}\n"
        f"- Workers: {result.workers}\n"
        f"- Model responses received: {received_responses}\n"
        f"- Valid JSON/schema responses: {valid_response_rate}\n"
        f"- Invalid JSON/schema responses: {result.invalid_responses}\n"
        f"- Transport errors: {result.transport_errors}"
    )


def append_summary(report_path: Path | str, summary: str) -> None:
    """Append aggregate counts without reading, replacing, or logging old notes."""
    path = Path(report_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    needs_separator = path.exists() and path.stat().st_size > 0
    with path.open("a", encoding="utf-8", newline="\n") as report:
        if needs_separator:
            report.write("\n\n")
        report.write(summary.rstrip())
        report.write("\n")


def build_argument_parser() -> argparse.ArgumentParser:
    parser = NonEchoingArgumentParser(description=__doc__)
    parser.add_argument(
        "--base-url",
        default=os.environ.get("LIFEQUEST_BASE_URL") or DEFAULT_BASE_URL,
        help="API base URL; defaults to LIFEQUEST_BASE_URL or the BigModel URL",
    )
    parser.add_argument(
        "--model",
        default=os.environ.get("LIFEQUEST_MODEL") or DEFAULT_MODEL,
        help="Model name; defaults to LIFEQUEST_MODEL or glm-4-flash",
    )
    parser.add_argument(
        "--requests-per-case",
        type=int,
        default=DEFAULT_REQUESTS_PER_CASE,
        help="Sequential live requests per goal case (default: 20)",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=DEFAULT_TIMEOUT_SECONDS,
        help="Timeout in seconds for each request (default: 240)",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=DEFAULT_WORKERS,
        help="Parallel requests across goals when greater than 1 (default: sequential)",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_argument_parser()
    args = parser.parse_args(argv)
    if args.requests_per_case < 1:
        parser.error("--requests-per-case must be at least 1")
    if args.timeout < 1:
        parser.error("--timeout must be at least 1")
    if args.workers < 1:
        parser.error("--workers must be at least 1")

    api_key = os.environ.get("LIFEQUEST_API_KEY")
    if not api_key:
        parser.print_usage(sys.stderr)
        parser.exit(2, "error: LIFEQUEST_API_KEY is not set in the environment\n")

    try:
        result = run_evaluation(
            api_key=api_key,
            base_url=args.base_url,
            model=args.model,
            requests_per_case=args.requests_per_case,
            timeout=args.timeout,
            workers=args.workers,
        )
        summary = format_summary(result)
        append_summary(REPORT_PATH, summary)
    except Exception:
        print("Evaluation failed; request and response details were not written.", file=sys.stderr)
        return 1

    print(summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
