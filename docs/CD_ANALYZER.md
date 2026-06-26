# CD log analyzer integration

When a Jenkins build **fails**, the pipeline runs `cd-analyze` and publishes a
**CD Failure Analysis** HTML report (plus `report.md` and `evidence.json`).

## Prerequisites

1. Logging stack working (pod logs in Elasticsearch — see `scripts/verify-logging.ps1`).
2. `cd-analyze` installed in the Jenkins container:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\install-cd-analyzer.ps1
```

Or run the full Jenkins patch (kubectl + docker + analyzer):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\patch-my-jenkins.ps1
```

## What the analyzer does

```
Build failed → triage (which stage?) → collect targeted evidence → filter → rules/LLM → report
```

| Source | When |
|--------|------|
| Workspace (JUnit, flow logs) | Always |
| Jenkins console window | Deploy/build failures |
| Elasticsearch (`traceId`, or `build` if no traceIds) | MAT/regression failures |
| kubectl pod logs | Deploy failures / escalation (weather-api + validation-service) |

Engine repo: [github.com/Sohan-Sabbana/CD-analyzer](https://github.com/Sohan-Sabbana/CD-analyzer)

### Rerun / stale-workspace behaviour

- **Surefire** is the source of truth for which tests failed on reruns.
- **traceIds** come from the flow report when flow names match; otherwise from `logs/flows/*.log` or `tests-*.json`.
- **ES queries** use traceId alone (no build filter) so pod logs still correlate when deploy was skipped or pods carry an older build tag.
- MAT/Regression stages **clear stale flow reports** before each run.

## Manual smoke test (no failed build required)

Uses build **#21** workspace + console log; pulls WARN lines from ES:

```powershell
docker exec -u jenkins jenkins cd-analyze `
  --workspace /var/jenkins_home/workspace/weather `
  --job weather --build 21 `
  --console-log /var/jenkins_home/jobs/weather/builds/21/log `
  --es-url http://host.docker.internal:9200 `
  --kube-ns weather `
  --out-dir /tmp/analyzer-smoke --print
```

## Off-agent polling (Jenkins REST API)

Run from your laptop — credentials live in **`scripts/jenkins/jenkins.local.env`** (gitignored).

```powershell
# 1) One-time setup
copy scripts\jenkins\jenkins.local.env.example scripts\jenkins\jenkins.local.env
# Edit jenkins.local.env — verified user for this Jenkins: sohansa

# 2) Verify token
powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\verify-jenkins-api.ps1

# 3) Poll + analyze (uses docker exec jenkins if cd-analyze not on PATH)
powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\poll-and-analyze.ps1 -Job weather -Build last -Print
```

Create token: **Jenkins → sohansa → Security → API Token → Add new token**

Manual `cd-analyze poll` (after loading env):

```powershell
. .\scripts\jenkins\load-jenkins-env.ps1
cd-analyze poll --job weather --build last --es-url http://localhost:9200 --out-dir .\analyzer-out --print
```

Archived artifacts must include flow reports, flow logs, and Surefire XML (see `Jenkinsfile` `archiveArtifacts`).

## See it in Jenkins

1. Trigger a failing build (e.g. temporarily break a test or deploy step).
2. Open the build → **CD Failure Analysis** (left sidebar).
3. Build description shows a one-line root cause when `evidence.json` is produced.

## Optional LLM analysis (plug-and-play providers)

The analyzer uses a **registry of LLM providers** — add a backend in CD-analyzer
without changing the Jenkins pipeline. Built-in providers: `groq`, `openai`.

### Jenkins (automatic on failure)

1. Create a Groq API key at [console.groq.com](https://console.groq.com).
2. **Manage Jenkins → Credentials → Global → Add Credentials**
   - Kind: **Secret text**
   - ID: `groq-api-key` (must match `Jenkinsfile`)
   - Secret: your Groq API key
3. On the next **failed** build, `cd-analyze` runs with:
   - `ANALYZER_LLM_PROVIDER=groq`
   - `ANALYZER_LLM_MODEL=llama-3.3-70b-versatile`

To switch to OpenAI later, change the `export` lines in `Jenkinsfile` to
`ANALYZER_LLM_PROVIDER=openai` and store the key under the same credential ID
(or a new one).

**When the LLM runs:** only when rule-based confidence is **below 0.6** (e.g.
`validation-service` rule at 0.55). Set `ANALYZER_LLM_FORCE=1` to test Groq
even on confident rule matches. The LLM only sees the capped evidence packet (~8 KB).

### Local / off-agent test

```powershell
$env:ANALYZER_LLM_PROVIDER = "groq"
$env:GROQ_API_KEY = "gsk_..."   # or ANALYZER_LLM_API_KEY
$env:ANALYZER_LLM_FORCE = "1"   # optional: force LLM on confident rules

docker exec -u jenkins -e ANALYZER_LLM_PROVIDER -e GROQ_API_KEY -e ANALYZER_LLM_FORCE jenkins cd-analyze `
  --workspace /var/jenkins_home/workspace/weather `
  --job weather --build 27 `
  --console-log /var/jenkins_home/jobs/weather/builds/27/log `
  --es-url http://host.docker.internal:9200 `
  --kube-ns weather `
  --kube-selector "app=weather-api,app=validation-service" `
  --out-dir /tmp/analyzer-llm-smoke --print
```

List registered components (including `llm_providers`):

```powershell
docker exec jenkins cd-analyze --list
```

## Branch / deploy notes

Until you merge to `main`:

1. Point the Jenkins job at `feature/cd-analyzer-integration` (multibranch scan or branch filter).
2. Reinstall the engine from the feature branch:
   ```powershell
   $env:CD_ANALYZER_REF = "feature/jenkins-remote-poll"
   powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\install-cd-analyzer.ps1
   ```
3. After merge, reinstall with `CD_ANALYZER_REF=main`.

