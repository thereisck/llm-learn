#!/usr/bin/env python3
"""
Week4 Day7 企业级RAG批量对比实验
三种模式对比：baseline / optimized / enterprise
"""
import json
import requests
import time
import sys

BASE_URL = "http://localhost:8900/rag"

# =================== 测试集 ===================
# 三类问句：🟢精确匹配 / 🟡语义模糊 / 🔴知识库外
TEST_SET = [
    # 🟢 精确匹配型（关键词直接命中）
    {"id": "Q1",  "category": "exact",   "question": "入职第一年有多少天年假？",                "expected_keywords": ["5天", "入职满1年"]},
    {"id": "Q2",  "category": "exact",   "question": "P7级别的基本月薪范围是多少？",            "expected_keywords": ["18000", "28000"]},
    {"id": "Q3",  "category": "exact",   "question": "NovaRAG的默认检索模式是什么？",           "expected_keywords": ["hybrid_rerank"]},
    {"id": "Q4",  "category": "exact",   "question": "密码最短需要多少位？",                    "expected_keywords": ["12位"]},
    {"id": "Q5",  "category": "exact",   "question": "NovaOSS标准存储每GB每月多少钱？",         "expected_keywords": ["0.12"]},

    # 🟡 语义模糊型（需要理解意图）
    {"id": "Q6",  "category": "semantic", "question": "我刚入职星云科技，想知道请假流程是怎样的？",       "expected_keywords": ["年假", "3个工作日", "飞书"]},
    {"id": "Q7",  "category": "semantic", "question": "公司有没有商业保险？保额多少？",              "expected_keywords": ["意外险", "50万", "补充医疗"]},
    {"id": "Q8",  "category": "semantic", "question": "新人入职第一天需要做什么？",                  "expected_keywords": ["签署合同", "领设备", "飞书"]},
    {"id": "Q9",  "category": "semantic", "question": "代码评审被拒绝了怎么办？",                   "expected_keywords": ["1-on-1", "重新提交", "第三方"]},
    {"id": "Q10", "category": "semantic", "question": "发现安全漏洞应该怎么报告？",                  "expected_keywords": ["飞书安全团队", "30分钟", "P0"]},

    # 🔴 知识库外型（答案不在库中）
    {"id": "Q11", "category": "out_of_domain", "question": "星云科技2025年的营收是多少？",        "expected_keywords": []},
    {"id": "Q12", "category": "out_of_domain", "question": "公司CEO的姓名是什么？",               "expected_keywords": []},
    {"id": "Q13", "category": "out_of_domain", "question": "如何申请公司停车位？",                 "expected_keywords": []},
    {"id": "Q14", "category": "out_of_domain", "question": "星云科技的股票代码是多少？",            "expected_keywords": []},
    {"id": "Q15", "category": "out_of_domain", "question": "公司食堂的菜单有哪些菜？",              "expected_keywords": []},
]

# =================== 三种模式配置 ===================
MODES = {
    "baseline": {
        "description": "纯向量检索 + 固定切分（Week3水平）",
        "api": "/query",
        "payload": lambda q: {"question": q, "threshold": 0.6, "searchMode": "vector", "compress": None},
    },
    "optimized": {
        "description": "hybrid_rerank + 上下文压缩（Week4优化版）",
        "api": "/enterprise/query/custom",
        "payload": lambda q: {
            "question": q,
            "searchMode": "hybrid_rerank",
            "threshold": 0.5,
            "enableCompression": True,
            "compressMode": "summary",
            "outOfDomainDetection": False,  # 关闭知识库外检测，看纯检索效果
        },
    },
    "enterprise": {
        "description": "完整企业版（hybrid_rerank + 知识库外检测 + 压缩）",
        "api": "/enterprise/query",
        "payload": lambda q: {"question": q},  # 使用默认EnterpriseRagConfig
    },
}

def call_api(mode_key, question):
    mode = MODES[mode_key]
    payload = mode["payload"](question)
    start = time.time()
    try:
        resp = requests.post(BASE_URL + mode["api"], json=payload, timeout=60)
        elapsed = time.time() - start
        if resp.status_code != 200:
            return {"error": f"HTTP {resp.status_code}", "elapsed": elapsed}
        data = resp.json()
        # 统一提取字段
        answer = data.get("answer", "")
        sources = data.get("sources", [])
        confidence = data.get("confidence", sources[0].get("score", 0) if sources else 0)
        out_of_domain = data.get("outOfDomain", False)
        search_mode_used = data.get("searchMode", mode_key)
        compress_mode_used = data.get("compressMode", "none")
        return {
            "answer": answer,
            "confidence": confidence,
            "out_of_domain": out_of_domain,
            "search_mode": search_mode_used,
            "compress_mode": compress_mode_used,
            "source_count": len(sources),
            "top_source": sources[0].get("source", "") if sources else "",
            "elapsed": elapsed,
        }
    except Exception as e:
        elapsed = time.time() - start
        return {"error": str(e), "elapsed": elapsed}

def evaluate_hit(answer, expected_keywords):
    """关键词命中率"""
    if not expected_keywords:
        return -1  # 知识库外问题不评估命中率
    hit = sum(1 for kw in expected_keywords if kw in answer)
    return hit / len(expected_keywords)

def evaluate_quality(answer, category):
    """粗略质量评分 1-5"""
    if category == "out_of_domain":
        # 知识库外问题：正确拒绝=5，编造信息=1
        reject_signals = ["不在", "无法", "没有", "范围", "咨询"]
        if any(s in answer for s in reject_signals):
            return 5
        return 1
    if not answer or len(answer) < 10:
        return 1
    # 有实质内容给3分起步
    score = 3
    if any(kw in answer for kw in ["根据", "依据", "参考", "来源", "文档", "条款"]):
        score += 1  # 有引用依据
    if len(answer) > 50 and len(answer) < 500:
        score += 0.5  # 简洁有力
    return min(score, 5)

def run_experiment():
    results = []
    print("=" * 80)
    print("企业级RAG批量对比实验")
    print("=" * 80)

    for mode_key in MODES:
        print(f"\n{'─' * 40}")
        print(f"模式: {mode_key} — {MODES[mode_key]['description']}")
        print(f"{'─' * 40}")

        for item in TEST_SET:
            qid = item["id"]
            question = item["question"]
            category = item["category"]
            expected = item["expected_keywords"]

            print(f"  {qid} [{category}] {question}")
            result = call_api(mode_key, question)

            if "error" in result:
                print(f"    ❌ 错误: {result['error']} ({result['elapsed']:.1f}s)")
                results.append({
                    "qid": qid, "mode": mode_key, "category": category,
                    "question": question, "error": result["error"],
                })
                continue

            answer_short = result["answer"][:80] + "..." if len(result["answer"]) > 80 else result["answer"]
            hit_rate = evaluate_hit(result["answer"], expected)
            quality = evaluate_quality(result["answer"], category)

            print(f"    ✅ confidence={result['confidence']:.4f} "
                  f"OOD={result['out_of_domain']} "
                  f"质量={quality:.1f} "
                  f"命中={hit_rate if hit_rate >= 0 else 'N/A'} "
                  f"耗时={result['elapsed']:.1f}s")
            print(f"    📝 {answer_short}")

            results.append({
                "qid": qid, "mode": mode_key, "category": category,
                "question": question,
                "answer": result["answer"],
                "confidence": result["confidence"],
                "out_of_domain": result["out_of_domain"],
                "search_mode": result["search_mode"],
                "compress_mode": result["compress_mode"],
                "source_count": result["source_count"],
                "top_source": result["top_source"],
                "hit_rate": hit_rate,
                "quality": quality,
                "elapsed": result["elapsed"],
            })

    # 保存原始数据
    with open("experiments/enterprise-qa-raw-2026-05-21.json", "w") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    # 统计汇总
    print("\n" + "=" * 80)
    print("实验汇总")
    print("=" * 80)

    for mode_key in MODES:
        mode_results = [r for r in results if r["mode"] == mode_key and "error" not in r]
        if not mode_results:
            continue

        exact = [r for r in mode_results if r["category"] == "exact"]
        semantic = [r for r in mode_results if r["category"] == "semantic"]
        ood = [r for r in mode_results if r["category"] == "out_of_domain"]

        avg_hit_exact = sum(r["hit_rate"] for r in exact) / len(exact) if exact else 0
        avg_quality_in = sum(r["quality"] for r in exact + semantic) / len(exact + semantic) if (exact + semantic) else 0
        avg_quality_ood = sum(r["quality"] for r in ood) / len(ood) if ood else 0
        avg_confidence = sum(r["confidence"] for r in mode_results) / len(mode_results) if mode_results else 0
        avg_elapsed = sum(r["elapsed"] for r in mode_results) / len(mode_results) if mode_results else 0
        ood_reject_rate = sum(1 for r in ood if r["out_of_domain"]) / len(ood) if ood else 0

        print(f"\n模式: {mode_key}")
        print(f"  精确匹配关键词命中率: {avg_hit_exact:.1%}")
        print(f"  知识库内问题质量评分: {avg_quality_in:.2f}/5")
        print(f"  知识库外问题质量评分: {avg_quality_ood:.2f}/5")
        print(f"  平均置信度: {avg_confidence:.4f}")
        print(f"  知识库外拒绝率: {ood_reject_rate:.1%}")
        print(f"  平均响应时间: {avg_elapsed:.1f}s")

    return results

if __name__ == "__main__":
    results = run_experiment()
    print(f"\n原始数据已保存到 experiments/enterprise-qa-raw-2026-05-21.json")