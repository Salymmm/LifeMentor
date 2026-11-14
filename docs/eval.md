# Goal decomposition evaluation

Run the live evaluation with:

```text
python tools/evaluate_decomposition.py
```

Set `LIFEQUEST_API_KEY` in the process environment before running. The harness
does not read credentials from files or command-line arguments. It sends
OpenAI-compatible `POST /chat/completions` requests to the configured base URL;
the default is `https://open.bigmodel.cn/api/paas/v4` with model `glm-4-flash`.
Set the nonsecret `LIFEQUEST_BASE_URL` or `LIFEQUEST_MODEL` environment
variables to override those defaults; `--base-url` and `--model` command-line
flags take precedence. Requests use temperature `0.7`, matching the app's chat
client. Each request has a 240-second timeout, matching the app's read timeout;
use `--timeout` to override it. Do not put credentials in the base URL.

Each run loads the evaluated `Prompts.DECOMPOSE_SYSTEM` constant from
`app/src/main/java/com/lifementor/app/llm/Prompts.kt`, including Kotlin string
interpolation, then evaluates ten
representative goals: job search, postgraduate entrance exams, career change,
budgeting and personal-finance education, English speaking, PMP preparation,
20 km cycling, a Python portfolio project, a side-business idea, and a
night-shift sleep routine. The fixed profiles include concrete time, budget,
location, equipment, and connectivity constraints. It makes 20 sequential requests per goal by default (200 total). Use
`--requests-per-case N` to choose a different count. Calls are sequential by
default; `--workers N` enables bounded concurrency across requests.

A response counts as valid only when it is standard JSON with exactly one root
key, `milestones`. Each milestone must have exactly `title` and `weeks` keys;
the title must start with `Q1 |`, `Q2 |`, `Q3 |`, or `Q4 |` and include a
nonblank suffix. Each milestone has 2–4 weeks. Each week must have exactly
`title` and `days` keys; its title must start with `Week N |` (where N is a
positive week number) and include a nonblank suffix. Every title and daily
action must contain printable English text; each week has 1–3 nonblank daily
actions, each at most 15 English words. Nonstandard JSON values
such as `NaN` and `Infinity` are rejected. Markdown-fenced JSON is accepted.
Invalid JSON/schema responses and transport/API errors are counted separately.
The valid-response rate is valid JSON/schema responses divided by all model
responses received; transport errors are excluded. If no model response
arrives, the rate is shown as N/A.

After a completed run, the harness appends a timestamp and aggregate counts to
this file. It preserves existing notes and never writes API keys, individual
goal results, or raw model replies to the report or console. Unit tests can be
run without network access:

```text
python -m unittest tools.test_evaluate_decomposition -v
```


### Evaluation run — 2026-11-14 14:16 UTC

- Goal cases: 10
- Requests per goal: 20
- Total requests: 200
- Model responses received: 0
- Valid JSON/schema responses: 0/0 (N/A; no model responses received)
- Invalid JSON/schema responses: 0
- Transport errors: 200


### Evaluation run — 2026-11-14 15:32 UTC

- Goal cases: 10
- Requests per goal: 2
- Total requests: 20
- Model responses received: 19
- Valid JSON/schema responses: 12/19 (63.2%)
- Invalid JSON/schema responses: 7
- Transport errors: 1


### Evaluation run — 2026-11-14 15:50 UTC

- Goal cases: 10
- Requests per goal: 2
- Total requests: 20
- Workers: 4
- Model responses received: 19
- Valid JSON/schema responses: 14/19 (73.7%)
- Invalid JSON/schema responses: 5
- Transport errors: 1


### Evaluation run — 2026-11-14 15:54 UTC

- Goal cases: 10
- Requests per goal: 2
- Total requests: 20
- Workers: 4
- Model responses received: 19
- Valid JSON/schema responses: 13/19 (68.4%)
- Invalid JSON/schema responses: 6
- Transport errors: 1


### Evaluation run — 2026-11-14 16:00 UTC

- Goal cases: 10
- Requests per goal: 2
- Total requests: 20
- Workers: 4
- Model responses received: 20
- Valid JSON/schema responses: 9/20 (45.0%)
- Invalid JSON/schema responses: 11
- Transport errors: 0

## Live benchmark verification - 2026-11-14

### Run 1 - 15:54 UTC

- Total requests: 20; responses: 19; valid JSON/schema: 13 (68.4% of responses, 65.0% of requests); invalid: 6; transport errors: 1; workers: 4; model: glm-4-flash.

### Run 2 - 16:00 UTC

- Total requests: 20; responses: 20; valid JSON/schema: 9 (45.0%); invalid: 11; transport errors: 0; workers: 4; model: glm-4-flash.
- Combined: 40 requests, 39 responses, 22 valid (56.4% of responses, 55.0% of requests), 17 invalid, and 1 transport error.

The 95% JSON/schema target was not met. These results remain a decomposition stability warning; they do not count as a passed quality gate.
