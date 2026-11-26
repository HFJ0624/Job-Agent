#!/usr/bin/env bash
# =====================================================================
# Eval 质量门禁（企业级对标 G3）
#
# 作用：跑核心数据集回归，passRate 低于绝对下限、或相对历史基线下降超过阈值时
#       失败（退出码 1），防止 Prompt/模型/RAG 变更把 Agent 质量改坏。
#
# 用法：
#   BASE_URL=http://192.168.10.100:8500 \
#   ADMIN_ACCOUNT=admin ADMIN_PASSWORD='your-password' \
#   DATASET_ID=1 ./scripts/eval-quality-gate.sh
#
# 环境变量：
#   BASE_URL        后端地址（默认 http://localhost:8500）
#   ADMIN_ACCOUNT   后台账号（与 ADMIN_PASSWORD 配对；也可直接传 SATOKEN 跳过登录）
#   ADMIN_PASSWORD  后台密码
#   SATOKEN         已登录的 sa-token 值（提供则跳过登录）
#   DATASET_ID      核心回归数据集 ID（必填）
#   MIN_PASS_RATE   通过率绝对下限（默认 0.80）
#   MAX_DROP        相对基线最大允许下降（默认 0.05）
#   BASELINE_FILE   基线文件路径（默认脚本同级 .eval-baseline）
#   TIMEOUT_SECONDS 批次执行超时（默认 600）
#   POLL_INTERVAL   轮询间隔秒（默认 5）
#
# 退出码：0 通过；1 未达门禁；2 用法/环境错误
# 依赖：curl、jq
# 说明：GitHub 托管 Runner 连不上开发库/模型 Key，请在 VM 或自建 Runner 上执行，
#       可用 cron 定时跑（见 docs/eval-quality-gate.md）。
# =====================================================================
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8500}"
MIN_PASS_RATE="${MIN_PASS_RATE:-0.80}"
MAX_DROP="${MAX_DROP:-0.05}"
BASELINE_FILE="${BASELINE_FILE:-$(dirname "$0")/.eval-baseline}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-600}"
POLL_INTERVAL="${POLL_INTERVAL:-5}"
DATASET_ID="${DATASET_ID:-}"

log() { echo "[eval-gate $(date '+%Y-%m-%d %H:%M:%S')] $*"; }
die() { echo "[eval-gate ERROR] $*" >&2; exit 2; }

command -v jq >/dev/null 2>&1 || die "缺少依赖 jq（apt/yum/brew install jq）"
command -v curl >/dev/null 2>&1 || die "缺少依赖 curl"
[ -n "$DATASET_ID" ] || die "必须提供 DATASET_ID（核心回归数据集 ID）"

# ---------- 1. 登录拿 sa-token（或复用外部 SATOKEN） ----------
TOKEN="${SATOKEN:-}"
TOKEN_NAME="satoken"
if [ -z "$TOKEN" ]; then
    [ -n "${ADMIN_ACCOUNT:-}" ] && [ -n "${ADMIN_PASSWORD:-}" ] \
        || die "需要 SATOKEN，或同时提供 ADMIN_ACCOUNT + ADMIN_PASSWORD"
    LOGIN_RESP=$(curl -sS -X POST "$BASE_URL/admin/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"account\":\"$ADMIN_ACCOUNT\",\"password\":\"$ADMIN_PASSWORD\"}")
    TOKEN=$(echo "$LOGIN_RESP" | jq -r '.data.tokenValue // empty')
    TOKEN_NAME=$(echo "$LOGIN_RESP" | jq -r '.data.tokenName // "satoken"')
    [ -n "$TOKEN" ] || die "登录失败：$LOGIN_RESP"
    log "登录成功（$ADMIN_ACCOUNT）"
fi

# ---------- 2. 触发数据集回归（异步：立即返回 RUNNING 批次） ----------
RUN_RESP=$(curl -sS -X POST "$BASE_URL/admin/agent/eval/run-dataset/$DATASET_ID" \
    -H "$TOKEN_NAME: $TOKEN")
RUN_ID=$(echo "$RUN_RESP" | jq -r '.data.id // empty')
[ -n "$RUN_ID" ] || die "触发回归失败：$RUN_RESP"
log "已触发数据集 $DATASET_ID 回归，runId=$RUN_ID"

# ---------- 3. 轮询批次状态直到 SUCCESS/FAILED ----------
DEADLINE=$(( $(date +%s) + TIMEOUT_SECONDS ))
STATUS=""
RUN_DETAIL=""
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    sleep "$POLL_INTERVAL"
    RUN_DETAIL=$(curl -sS "$BASE_URL/admin/agent/eval/runs/$RUN_ID" -H "$TOKEN_NAME: $TOKEN")
    STATUS=$(echo "$RUN_DETAIL" | jq -r '.data.status // empty')
    if [ "$STATUS" = "SUCCESS" ] || [ "$STATUS" = "FAILED" ]; then
        break
    fi
done
[ "$STATUS" = "SUCCESS" ] || [ "$STATUS" = "FAILED" ] \
    || die "批次执行超时（${TIMEOUT_SECONDS}s），最后状态：${STATUS:-未知}"

TOTAL=$(echo "$RUN_DETAIL" | jq -r '.data.totalCount // 0')
PASS=$(echo "$RUN_DETAIL" | jq -r '.data.passCount // 0')
[ "$TOTAL" -gt 0 ] 2>/dev/null || die "批次没有用例（totalCount=0），请检查数据集 $DATASET_ID"
PASS_RATE=$(awk "BEGIN{printf \"%.4f\", $PASS/$TOTAL}")
log "批次完成：status=$STATUS，通过 $PASS/$TOTAL（passRate=$PASS_RATE）"

# ---------- 4. 门禁判定：绝对下限 + 相对基线下降 ----------
BASELINE_RATE=""
if [ -f "$BASELINE_FILE" ]; then
    BASELINE_RATE=$(tr -d '[:space:]' < "$BASELINE_FILE")
fi

FAIL_REASONS=""
if awk "BEGIN{exit !($PASS_RATE < $MIN_PASS_RATE)}"; then
    FAIL_REASONS="$FAIL_REASONS；passRate=$PASS_RATE 低于下限 $MIN_PASS_RATE"
fi
if [ -n "$BASELINE_RATE" ] && awk "BEGIN{exit !($BASELINE_RATE - $PASS_RATE > $MAX_DROP)}"; then
    FAIL_REASONS="$FAIL_REASONS；passRate 较基线 $BASELINE_RATE 下降超过 $MAX_DROP"
fi

if [ -n "$FAIL_REASONS" ]; then
    log "❌ 质量门禁未通过：$FAIL_REASONS"
    log "   运行批次 runId=$RUN_ID，请到 Admin Eval 页面查看失败详情（含 Judge 理由）"
    exit 1
fi

# ---------- 5. 通过：更新基线（只在通过时更新，防止把退化固化成新基线） ----------
echo "$PASS_RATE" > "$BASELINE_FILE"
log "✅ 质量门禁通过，基线已更新为 $PASS_RATE"
exit 0
