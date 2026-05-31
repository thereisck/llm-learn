#!/bin/bash
# Week4 Day7 企业级RAG批量对比实验
# 三种模式: baseline(vector+固定切分) / optimized(hybrid_rerank+压缩) / enterprise(完整企业版+OOD检测)

BASE="http://localhost:8900/rag"
OUTFILE="experiments/enterprise-qa-benchmark-2026-05-21.json"

echo "========================================================"
echo "企业级RAG批量对比实验 - $(date '+%Y-%m-%d %H:%M')"
echo "========================================================"

# 15个测试问句
QUESTIONS=(
  # 🟢 精确匹配型
  'Q1|exact|入职第一年有多少天年假？|5天,入职满1年'
  'Q2|exact|P7级别的基本月薪范围是多少？|18000,28000'
  'Q3|exact|NovaRAG的默认检索模式是什么？|hybrid_rerank'
  'Q4|exact|密码最短需要多少位？|12位'
  'Q5|exact|NovaOSS标准存储每GB每月多少钱？|0.12'
  # 🟡 语义模糊型
  'Q6|semantic|我刚入职星云科技，想知道请假流程是怎样的？|年假,3个工作日,飞书'
  'Q7|semantic|公司有没有商业保险？保额多少？|意外险,50万,补充医疗'
  'Q8|semantic|新人入职第一天需要做什么？|签署合同,领设备,飞书'
  'Q9|semantic|代码评审被拒绝了怎么办？|1-on-1,重新提交,第三方'
  'Q10|semantic|发现安全漏洞应该怎么报告？|飞书安全团队,30分钟,P0'
  # 🔴 知识库外型
  'Q11|ood|星云科技2025年的营收是多少？|'
  'Q12|ood|公司CEO的姓名是什么？|'
  'Q13|ood|如何申请公司停车位？|'
  'Q14|ood|星云科技的股票代码是多少？|'
  'Q15|ood|公司食堂的菜单有哪些菜？|'
)

# 初始化JSON结果
echo '[]' > /tmp/qa_results.json

run_query() {
  local mode="$1" qid="$2" category="$3" question="$4" expected="$5"
  
  case "$mode" in
    baseline)
      PAYLOAD="{\"question\":\"$question\",\"threshold\":0.6,\"searchMode\":\"vector\"}"
      API="/query"
      ;;
    optimized)
      PAYLOAD="{\"question\":\"$question\",\"searchMode\":\"hybrid_rerank\",\"threshold\":0.5,\"enableCompression\":true,\"compressMode\":\"summary\",\"outOfDomainDetection\":false}"
      API="/enterprise/query/custom"
      ;;
    enterprise)
      PAYLOAD="{\"question\":\"$question\"}"
      API="/enterprise/query"
      ;;
  esac
  
  START=$(python3 -c "import time; print(time.time())")
  RESP=$(curl -s -X POST "$BASE$API" -H "Content-Type: application/json" -d "$PAYLOAD" 2>&1)
  END=$(python3 -c "import time; print(time.time())")
  ELAPSED=$(python3 -c "print(f'{$END - $START:.1f}')")
  
  # 提取字段
  ANSWER=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('answer','ERROR'))" 2>/dev/null || echo "PARSE_ERROR")
  CONFIDENCE=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'{d.get(\"confidence\",0):.4f}')" 2>/dev/null || echo "0")
  OOD=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('outOfDomain',False))" 2>/dev/null || echo "False")
  SRC_COUNT=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('sources',[])))" 2>/dev/null || echo "0")
  
  # 转义answer中的特殊字符以便JSON写入
  ANSWER_ESC=$(echo "$ANSWER" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read().strip())[1:-1])")
  
  # 添加到结果JSON
  python3 -c "
import json
results = json.load(open('/tmp/qa_results.json'))
results.append({
    'qid': '$qid', 'mode': '$mode', 'category': '$category',
    'question': '$question', 'answer': '$ANSWER_ESC',
    'confidence': '$CONFIDENCE', 'out_of_domain': '$OOD',
    'source_count': '$SRC_COUNT', 'elapsed': '$ELAPSED',
    'expected_keywords': '$expected'
})
json.dump(results, open('/tmp/qa_results.json','w'), ensure_ascii=False, indent=2)
"
  
  printf "  %-4s [%-8s] conf=%-8s OOD=%-6s src=%-3s t=%-5ss → %s\n" \
    "$qid" "$category" "$CONFIDENCE" "$OOD" "$SRC_COUNT" "$ELAPSED" \
    "$(echo "$ANSWER" | head -c 60)"
}

for MODE in baseline optimized enterprise; do
  echo ""
  echo "────────────────────────────────────────────────────────"
  echo "模式: $MODE"
  echo "────────────────────────────────────────────────────────"
  
  for ITEM in "${QUESTIONS[@]}"; do
    IFS='|' read -r QID CAT QUESTION EXPECTED <<< "$ITEM"
    run_query "$MODE" "$QID" "$CAT" "$QUESTION" "$EXPECTED"
    sleep 1  # 避免API限流
  done
done

# 复制最终结果
cp /tmp/qa_results.json "$OUTFILE"
echo ""
echo "========================================================"
echo "原始数据已保存到 $OUTFILE"
echo "========================================================"

# 生成统计汇总
python3 << 'PYEOF'
import json

with open("experiments/enterprise-qa-benchmark-2026-05-21.json") as f:
    results = json.load(f)

modes = ["baseline", "optimized", "enterprise"]
categories = {"exact": "🟢精确匹配", "semantic": "🟡语义模糊", "ood": "🔴知识库外"}

print("\n" + "=" * 60)
print("📊 实验统计汇总")
print("=" * 60)

for mode in modes:
    mr = [r for r in results if r["mode"] == mode]
    if not mr:
        continue
    
    exact = [r for r in mr if r["category"] == "exact"]
    sem = [r for r in mr if r["category"] == "semantic"]
    ood = [r for r in mr if r["category"] == "ood"]
    
    # 关键词命中率（仅精确匹配）
    def hit_rate(r):
        exp = r.get("expected_keywords", "")
        if not exp:
            return None
        kws = [k.strip() for k in exp.split(",")]
        ans = r.get("answer", "")
        hit = sum(1 for k in kws if k in ans)
        return hit / len(kws)
    
    exact_hits = [hit_rate(r) for r in exact]
    avg_hit = sum(h for h in exact_hits if h is not None) / len(exact_hits) if exact_hits else 0
    
    # 知识库外拒绝率
    ood_reject = sum(1 for r in ood if str(r.get("out_of_domain","")).lower() == "true") / len(ood) if ood else 0
    
    # 平均置信度
    avg_conf = sum(float(r.get("confidence", 0)) for r in mr) / len(mr)
    
    # 平均响应时间
    avg_time = sum(float(r.get("elapsed", 0)) for r in mr) / len(mr)
    
    # OOD问题答案中含拒绝信号的比例
    ood_quality = 0
    reject_signals = ["不在", "无法", "没有", "范围", "咨询", "相关部门"]
    for r in ood:
        ans = r.get("answer", "")
        if any(s in ans for s in reject_signals):
            ood_quality += 1
    ood_reject_detail = ood_quality / len(ood) if ood else 0
    
    print(f"\n📌 {mode}")
    print(f"   精确匹配关键词命中率: {avg_hit:.1%}")
    print(f"   平均置信度:           {avg_conf:.4f}")
    print(f"   知识库外拒绝率(API):   {ood_reject:.1%}")
    print(f"   知识库外拒绝率(答案):  {ood_reject_detail:.1%}")
    print(f"   平均响应时间:          {avg_time:.1f}s")

# 逐问题对比
print("\n" + "=" * 60)
print("📋 逐问题对比（置信度 & OOD标记）")
print("=" * 60)
print(f"{'QID':4s} {'类别':8s} {'问题':30s} │ {'baseline':10s} │ {'optimized':10s} │ {'enterprise':10s}")
print("-" * 100)

for item in results[:5]:  # 只看精确匹配的前5个
    qid = item["qid"]
    if qid not in ["Q1","Q2","Q3","Q4","Q5","Q6","Q7","Q8","Q9","Q10","Q11","Q12","Q13","Q14","Q15"]:
        continue
    
    row_base = [r for r in results if r["qid"] == qid and r["mode"] == "baseline"]
    row_opt = [r for r in results if r["qid"] == qid and r["mode"] == "optimized"]
    row_ent = [r for r in results if r["qid"] == qid and r["mode"] == "enterprise"]
    
    b_conf = f"{float(row_base[0].get('confidence',0)):.4f}" if row_base else "N/A"
    o_conf = f"{float(row_opt[0].get('confidence',0)):.4f}" if row_opt else "N/A"
    e_conf = f"{float(row_ent[0].get('confidence',0)):.4f}" if row_ent else "N/A"
    
    b_ood = str(row_base[0].get('out_of_domain','N/A')) if row_base else "N/A"
    o_ood = str(row_opt[0].get('out_of_domain','N/A')) if row_opt else "N/A"
    e_ood = str(row_ent[0].get('out_of_domain','N/A')) if row_ent else "N/A"
    
    cat = item["category"]
    q = item["question"][:28]
    print(f"{qid:4s} {cat:8s} {q:30s} │ {b_conf}/{b_ood:5s} │ {o_conf}/{o_ood:5s} │ {e_conf}/{e_ood:5s}")

PYEOF