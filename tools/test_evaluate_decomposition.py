import copy
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr
from pathlib import Path
from unittest.mock import patch

from tools import evaluate_decomposition as evaluation


def valid_decomposition(marker="Job search action"):
    return {
        "milestones": [
            {
                "title": f"Q1 | {marker} one",
                "weeks": [
                    {"title": "Week 1 | Prepare", "days": ["List target roles"]},
                    {"title": "Week 2 | Apply", "days": ["Update the resume"]},
                ],
            },
            {
                "title": f"Q2 | {marker} two",
                "weeks": [
                    {"title": "Week 3 | Practice", "days": ["Practice the introduction"]},
                    {"title": "Week 4 | Review", "days": ["Record interview feedback"]},
                ],
            },
        ]
    }


def prompt_fixture(directory):
    source = directory / "Prompts.kt"
    source.write_text(
        'const val SYSTEM = """unrelated"""\n'
        'const val DECOMPOSE_SYSTEM = """真实提示第一行\n'
        '第二行内容\n"""\n'
        'const val REVIEW_SYSTEM = """later prompt"""\n',
        encoding="utf-8",
    )
    return source


class PromptExtractionTests(unittest.TestCase):
    def test_reads_the_decomposition_constant_without_changing_its_contents(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            source = prompt_fixture(Path(temporary_directory))

            prompt = evaluation.extract_system_prompt(source)

        self.assertEqual(prompt, "真实提示第一行\n第二行内容\n")


class ModelResponseTests(unittest.TestCase):
    def test_extracts_plain_and_markdown_fenced_json(self):
        expected = {"milestones": []}

        self.assertEqual(evaluation.parse_model_response('{"milestones": []}'), expected)
        self.assertEqual(
            evaluation.parse_model_response('```json\n{"milestones": []}\n```'),
            expected,
        )

    def test_rejects_non_json_without_exposing_response_text_in_error(self):
        raw_reply = "SENSITIVE_RAW_MODEL_REPLY"

        with self.assertRaises(evaluation.InvalidModelResponse) as raised:
            evaluation.parse_model_response(raw_reply)

        self.assertNotIn(raw_reply, str(raised.exception))

    def test_rejects_python_nonstandard_nan_and_infinity_constants(self):
        for index, raw_response in enumerate(
            ["NaN", "Infinity", "-Infinity", '{"milestones": NaN}']
        ):
            with self.subTest(index=index):
                with self.assertRaises(evaluation.InvalidModelResponse):
                    evaluation.parse_model_response(raw_response)


class SchemaValidationTests(unittest.TestCase):
    def test_accepts_schema_at_the_app_contract_bounds(self):
        self.assertTrue(evaluation.is_valid_decomposition(valid_decomposition()))

    def test_rejects_out_of_bounds_and_blank_daily_actions(self):
        mutations = []

        too_few_milestones = copy.deepcopy(valid_decomposition())
        too_few_milestones["milestones"].pop()
        mutations.append(too_few_milestones)

        too_many_milestones = copy.deepcopy(valid_decomposition())
        too_many_milestones["milestones"].extend(
            copy.deepcopy(
                [
                    too_many_milestones["milestones"][0],
                    too_many_milestones["milestones"][1],
                    too_many_milestones["milestones"][0],
                ]
            )
        )
        mutations.append(too_many_milestones)

        too_few_weeks = copy.deepcopy(valid_decomposition())
        too_few_weeks["milestones"][0]["weeks"].pop()
        mutations.append(too_few_weeks)

        too_many_weeks = copy.deepcopy(valid_decomposition())
        too_many_weeks["milestones"][0]["weeks"].extend(
            copy.deepcopy(
                [
                    too_many_weeks["milestones"][0]["weeks"][0],
                    too_many_weeks["milestones"][0]["weeks"][1],
                    too_many_weeks["milestones"][0]["weeks"][0],
                ]
            )
        )
        mutations.append(too_many_weeks)

        no_actions = copy.deepcopy(valid_decomposition())
        no_actions["milestones"][0]["weeks"][0]["days"] = []
        mutations.append(no_actions)

        too_many_actions = copy.deepcopy(valid_decomposition())
        too_many_actions["milestones"][0]["weeks"][0]["days"] = ["a", "b", "c", "d"]
        mutations.append(too_many_actions)

        blank_action = copy.deepcopy(valid_decomposition())
        blank_action["milestones"][0]["weeks"][0]["days"] = ["   "]
        mutations.append(blank_action)

        long_action = copy.deepcopy(valid_decomposition())
        long_action["milestones"][0]["weeks"][0]["days"] = [
            "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen"
        ]
        mutations.append(long_action)

        for index, invalid in enumerate(mutations):
            with self.subTest(index=index):
                self.assertFalse(evaluation.is_valid_decomposition(invalid))

    def test_daily_action_limit_is_fifteen_english_words(self):
        fifteen_words = copy.deepcopy(valid_decomposition())
        fifteen_words["milestones"][0]["weeks"][0]["days"] = [
            "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen"
        ]

        sixteen_words = copy.deepcopy(fifteen_words)
        sixteen_words["milestones"][0]["weeks"][0]["days"] = [
            "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen"
        ]

        self.assertTrue(evaluation.is_valid_decomposition(fifteen_words))
        self.assertFalse(evaluation.is_valid_decomposition(sixteen_words))

    def test_rejects_non_english_text_in_nested_fields(self):
        chinese_action = copy.deepcopy(valid_decomposition())
        chinese_action["milestones"][0]["weeks"][0]["days"] = ["完成今天的练习"]

        chinese_title = copy.deepcopy(valid_decomposition())
        chinese_title["milestones"][0]["title"] = "Q1 | 准备求职"

        self.assertFalse(evaluation.is_valid_decomposition(chinese_action))
        self.assertFalse(evaluation.is_valid_decomposition(chinese_title))

    def test_rejects_extra_keys_at_each_object_level(self):
        root_extra = copy.deepcopy(valid_decomposition())
        root_extra["commentary"] = "unexpected"

        milestone_extra = copy.deepcopy(valid_decomposition())
        milestone_extra["milestones"][0]["commentary"] = "unexpected"

        week_extra = copy.deepcopy(valid_decomposition())
        week_extra["milestones"][0]["weeks"][0]["commentary"] = "unexpected"

        for index, invalid in enumerate([root_extra, milestone_extra, week_extra]):
            with self.subTest(index=index):
                self.assertFalse(evaluation.is_valid_decomposition(invalid))

    def test_requires_prompt_milestone_and_week_title_prefixes(self):
        wrong_milestone_prefix = copy.deepcopy(valid_decomposition())
        wrong_milestone_prefix["milestones"][0]["title"] = "Q5 | Prepare job search"

        missing_milestone_prefix = copy.deepcopy(valid_decomposition())
        missing_milestone_prefix["milestones"][0]["title"] = "First quarter job search"

        wrong_week_prefix = copy.deepcopy(valid_decomposition())
        wrong_week_prefix["milestones"][0]["weeks"][0]["title"] = "First week preparation"

        empty_milestone_suffix = copy.deepcopy(valid_decomposition())
        empty_milestone_suffix["milestones"][0]["title"] = "Q1 |   "

        empty_week_suffix = copy.deepcopy(valid_decomposition())
        empty_week_suffix["milestones"][0]["weeks"][0]["title"] = "Week 1 |   "

        invalid_cases = [
            wrong_milestone_prefix,
            missing_milestone_prefix,
            wrong_week_prefix,
            empty_milestone_suffix,
            empty_week_suffix,
        ]
        for index, invalid in enumerate(invalid_cases):
            with self.subTest(index=index):
                self.assertFalse(evaluation.is_valid_decomposition(invalid))


class EvaluationHarnessTests(unittest.TestCase):
    def test_parallel_workers_preserve_aggregate_counts(self):
        def fake_request(base_url, model, api_key, system_prompt, user_message, timeout):
            return json.dumps(valid_decomposition(), ensure_ascii=False)

        with tempfile.TemporaryDirectory() as temporary_directory:
            result = evaluation.run_evaluation(
                api_key="TEST_KEY_DO_NOT_ECHO",
                model="test-model",
                prompt_path=prompt_fixture(Path(temporary_directory)),
                requests_per_case=2,
                cases=evaluation.GOAL_CASES[:2],
                request_function=fake_request,
                workers=4,
            )

        self.assertEqual(result.case_count, 2)
        self.assertEqual(result.requests_per_case, 2)
        self.assertEqual(result.attempts, 4)
        self.assertEqual(result.valid_responses, 4)
        self.assertEqual(result.invalid_responses, 0)
        self.assertEqual(result.transport_errors, 0)

    def test_goal_cases_are_unique_and_have_complete_concrete_inputs(self):
        self.assertEqual(len(evaluation.GOAL_CASES), 10)
        self.assertEqual(
            len({case.title for case in evaluation.GOAL_CASES}),
            10,
        )

        for case in evaluation.GOAL_CASES:
            with self.subTest(title=case.title):
                self.assertTrue(case.title.strip())
                self.assertTrue(case.domain_label.strip())
                self.assertTrue(case.role.strip())
                self.assertTrue(case.stage.strip())
                self.assertTrue(case.vision.strip())
                self.assertTrue(case.context.strip())
                self.assertTrue(case.profile)
                self.assertTrue(
                    all(
                        str(key).strip() and str(value).strip()
                        for key, value in case.profile.items()
                    )
                )

    def test_extracts_runtime_string_interpolation(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            prompts_path = Path(temporary_directory) / "Prompts.kt"
            prompts_path.write_text(
                'object Prompts {\n'
                '    private const val ENGLISH_ONLY_RULE =\n'
                '        "Use English only."\n'
                '    const val DECOMPOSE_SYSTEM = """Return JSON. $ENGLISH_ONLY_RULE"""\n'
                '}\n',
                encoding="utf-8",
            )

            prompt = evaluation.extract_system_prompt(prompts_path)

        self.assertEqual(prompt, "Return JSON. Use English only.")

    def test_cli_defaults_and_environment_overrides_are_overridable_by_flags(self):
        with patch.dict(os.environ, {}, clear=True):
            defaults = evaluation.build_argument_parser().parse_args([])

        self.assertEqual(defaults.base_url, "https://open.bigmodel.cn/api/paas/v4")
        self.assertEqual(defaults.model, "glm-4-flash")
        self.assertEqual(defaults.timeout, 240)

        with patch.dict(
            os.environ,
            {
                "LIFEQUEST_BASE_URL": "https://env.example/v4",
                "LIFEQUEST_MODEL": "env-model",
            },
            clear=True,
        ):
            from_environment = evaluation.build_argument_parser().parse_args([])
            from_flags = evaluation.build_argument_parser().parse_args(
                ["--base-url", "https://cli.example/v4", "--model", "cli-model"]
            )

        self.assertEqual(from_environment.base_url, "https://env.example/v4")
        self.assertEqual(from_environment.model, "env-model")
        self.assertEqual(from_flags.base_url, "https://cli.example/v4")
        self.assertEqual(from_flags.model, "cli-model")

    def test_chat_request_uses_compatible_endpoint_and_app_temperature(self):
        captured = {}

        def fake_urlopen(request, timeout):
            captured["url"] = request.full_url
            captured["method"] = request.get_method()
            captured["authorization"] = request.get_header("Authorization")
            captured["timeout"] = timeout
            captured["payload"] = json.loads(request.data.decode("utf-8"))
            return io.BytesIO(
                json.dumps({"choices": [{"message": {"content": "{}"}}]}).encode("utf-8")
            )

        with patch.object(evaluation.urllib.request, "urlopen", side_effect=fake_urlopen):
            content = evaluation.request_chat_completion(
                "https://open.bigmodel.cn/api/paas/v4/",
                "glm-4-flash",
                "TEST_KEY_DO_NOT_ECHO",
                "system text",
                "user text",
                timeout=11,
            )

        self.assertEqual(content, "{}")
        self.assertEqual(captured["url"], "https://open.bigmodel.cn/api/paas/v4/chat/completions")
        self.assertEqual(captured["method"], "POST")
        self.assertEqual(captured["authorization"], "Bearer TEST_KEY_DO_NOT_ECHO")
        self.assertEqual(captured["timeout"], 11)
        self.assertEqual(captured["payload"]["model"], "glm-4-flash")
        self.assertEqual(captured["payload"]["temperature"], 0.7)
        self.assertEqual(
            captured["payload"]["messages"],
            [
                {"role": "system", "content": "system text"},
                {"role": "user", "content": "user text"},
            ],
        )

    def test_default_run_makes_twenty_sequential_requests_for_each_of_ten_cases(self):
        seen_goals = []
        seen_timeouts = []

        def fake_request(base_url, model, api_key, system_prompt, user_message, timeout):
            seen_goals.append(user_message.splitlines()[0])
            seen_timeouts.append(timeout)
            return json.dumps(valid_decomposition(), ensure_ascii=False)

        with tempfile.TemporaryDirectory() as temporary_directory:
            result = evaluation.run_evaluation(
                api_key="TEST_KEY_DO_NOT_ECHO",
                model="test-model",
                prompt_path=prompt_fixture(Path(temporary_directory)),
                request_function=fake_request,
            )

        self.assertEqual(result.case_count, 10)
        self.assertEqual(result.requests_per_case, 20)
        self.assertEqual(result.attempts, 200)
        self.assertEqual(result.valid_responses, 200)
        expected_goals = [f"目标：{case.title}" for case in evaluation.GOAL_CASES for _ in range(20)]
        self.assertEqual(seen_goals, expected_goals)
        self.assertEqual(seen_timeouts, [240] * 200)

    def test_appends_only_aggregate_summary_and_preserves_existing_notes(self):
        raw_marker = "SENSITIVE_RAW_MODEL_REPLY_MARKER"
        secret_marker = "TEST_KEY_DO_NOT_ECHO"
        payload = valid_decomposition(raw_marker)

        def fake_request(base_url, model, api_key, system_prompt, user_message, timeout):
            return json.dumps(payload, ensure_ascii=False)

        with tempfile.TemporaryDirectory() as temporary_directory:
            directory = Path(temporary_directory)
            source = prompt_fixture(directory)
            report = directory / "eval.md"
            report.write_text("Existing human notes stay here.\n", encoding="utf-8")
            result = evaluation.run_evaluation(
                api_key=secret_marker,
                model="test-model",
                prompt_path=source,
                requests_per_case=1,
                cases=[evaluation.GOAL_CASES[0]],
                request_function=fake_request,
            )

            summary = evaluation.format_summary(result)
            evaluation.append_summary(report, summary)
            saved_report = report.read_text(encoding="utf-8")

        self.assertIn("Existing human notes stay here.", saved_report)
        self.assertIn("Valid JSON/schema responses: 1", saved_report)
        self.assertIn("Transport errors: 0", saved_report)
        self.assertNotIn(secret_marker, summary)
        self.assertNotIn(secret_marker, saved_report)
        self.assertNotIn(raw_marker, summary)
        self.assertNotIn(raw_marker, saved_report)

    def test_counts_transport_failures_separately_from_model_responses(self):
        def failing_request(base_url, model, api_key, system_prompt, user_message, timeout):
            raise evaluation.TransportError()

        with tempfile.TemporaryDirectory() as temporary_directory:
            result = evaluation.run_evaluation(
                api_key="TEST_KEY_DO_NOT_ECHO",
                prompt_path=prompt_fixture(Path(temporary_directory)),
                requests_per_case=1,
                cases=[evaluation.GOAL_CASES[0]],
                request_function=failing_request,
            )

        self.assertEqual(result.valid_responses, 0)
        self.assertEqual(result.invalid_responses, 0)
        self.assertEqual(result.transport_errors, 1)

    def test_valid_rate_uses_model_responses_and_excludes_transport_errors(self):
        result = evaluation.EvaluationResult(
            case_count=2,
            requests_per_case=2,
            valid_responses=1,
            invalid_responses=1,
            transport_errors=2,
            timestamp_utc="2026-11-14 00:00 UTC",
        )

        summary = evaluation.format_summary(result)

        self.assertIn("Model responses received: 2", summary)
        self.assertIn("Valid JSON/schema responses: 1/2 (50.0%)", summary)
        self.assertIn("Transport errors: 2", summary)

    def test_valid_rate_is_not_applicable_when_no_model_responses_arrive(self):
        result = evaluation.EvaluationResult(
            case_count=1,
            requests_per_case=2,
            valid_responses=0,
            invalid_responses=0,
            transport_errors=2,
            timestamp_utc="2026-11-14 00:00 UTC",
        )

        summary = evaluation.format_summary(result)

        self.assertIn("Model responses received: 0", summary)
        self.assertIn("Valid JSON/schema responses: 0/0 (N/A; no model responses received)", summary)
        self.assertIn("Transport errors: 2", summary)

    def test_cli_argument_errors_do_not_echo_sensitive_values(self):
        sensitive_value = "TEST_KEY_DO_NOT_ECHO"
        error_output = io.StringIO()

        with redirect_stderr(error_output):
            with self.assertRaises(SystemExit):
                evaluation.main(["--requests-per-case", sensitive_value])

        self.assertNotIn(sensitive_value, error_output.getvalue())


if __name__ == "__main__":
    unittest.main()
