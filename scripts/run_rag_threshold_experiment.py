#!/usr/bin/env python3
import json
import time
import urllib.request
from pathlib import Path

endpoint = 'http://localhost:8900/rag/query'
thresholds = [0.20, 0.35, 0.50, 0.65, 0.80]
questions = [
    ('A1', '精确命中', 'RAG 的核心流程包括哪几步？'),
    ('A2', '精确命中', 'topK 和 threshold 分别控制什么？'),
    ('A3', '精确命中', 'Rerank 在 RAG 中有什么作用？'),
    ('B1', '语义改写', '为什么 RAG 不能只靠大模型自己回答？'),
    ('B2', '语义改写', '文档切得太碎会有什么问题？'),
    ('B3', '语义改写', '中文知识库适合用哪些 Embedding 模型？'),
    ('C1', '半相关', 'Elasticsearch 为什么适合 Java 后端做 RAG？'),
    ('C2', '半相关', '关键词检索和向量检索各自擅长什么？'),
    ('D1', '知识库外', 'RAG 系统如何做用户权限隔离？'),
    ('D2', '知识库外', '如何用 Redis 缓存 RAG 检索结果？'),
]


def call(question: str, threshold: float) -> dict:
    data = json.dumps({'question': question, 'threshold': threshold}, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(endpoint, data=data, headers={'Content-Type': 'application/json'}, method='POST')
    with urllib.request.urlopen(req, timeout=90) as resp:
        return json.loads(resp.read().decode('utf-8'))


def main():
    out_dir = Path('experiments')
    out_dir.mkdir(exist_ok=True)
    raw_path = out_dir / 'rag-threshold-raw-2026-05-13.jsonl'
    md_path = out_dir / 'rag-threshold-summary-2026-05-13.md'

    rows = []
    with raw_path.open('w', encoding='utf-8') as raw:
        for th in thresholds:
            for qid, qtype, q in questions:
                print(f'RUN threshold={th:.2f} {qid} {q}', flush=True)
                started = time.time()
                try:
                    res = call(q, th)
                    elapsed = round(time.time() - started, 3)
                    sources = res.get('sources') or []
                    scores = [float(s.get('score', 0)) for s in sources]
                    answer = res.get('answer', '')
                    row = {
                        'threshold': th,
                        'qid': qid,
                        'type': qtype,
                        'question': q,
                        'source_count': len(sources),
                        'max_score': max(scores) if scores else None,
                        'min_score': min(scores) if scores else None,
                        'chunks': [s.get('chunkIndex') for s in sources],
                        'answer_preview': answer.replace('\n', ' ')[:120],
                        'elapsed_sec': elapsed,
                        'ok': True,
                        'response': res,
                    }
                except Exception as e:
                    row = {
                        'threshold': th,
                        'qid': qid,
                        'type': qtype,
                        'question': q,
                        'source_count': None,
                        'max_score': None,
                        'min_score': None,
                        'chunks': [],
                        'answer_preview': f'ERROR: {e}',
                        'elapsed_sec': round(time.time() - started, 3),
                        'ok': False,
                        'error': repr(e),
                    }
                raw.write(json.dumps(row, ensure_ascii=False) + '\n')
                raw.flush()
                rows.append(row)

    with md_path.open('w', encoding='utf-8') as md:
        md.write('# RAG Threshold 实验汇总（2026-05-13）\n\n')
        md.write('- endpoint: `POST http://localhost:8900/rag/query`\n')
        md.write('- body: `{ "question": "...", "threshold": 0.0 }`\n')
        md.write('- questions: 10\n')
        md.write('- thresholds: 0.20 / 0.35 / 0.50 / 0.65 / 0.80\n\n')
        for th in thresholds:
            md.write(f'## threshold = {th:.2f}\n\n')
            md.write('| ID | 类型 | 召回数 | 最高分 | 最低分 | chunks | 答案预览 |\n')
            md.write('|---|---|---:|---:|---:|---|---|\n')
            for r in [x for x in rows if x['threshold'] == th]:
                maxs = '' if r['max_score'] is None else f"{r['max_score']:.4f}"
                mins = '' if r['min_score'] is None else f"{r['min_score']:.4f}"
                chunks = ','.join(map(str, r['chunks']))
                preview = r['answer_preview'].replace('|', '\\|')
                md.write(f"| {r['qid']} | {r['type']} | {r['source_count']} | {maxs} | {mins} | {chunks} | {preview} |\n")
            md.write('\n')
        md.write('## 问句清单\n\n')
        for qid, qtype, q in questions:
            md.write(f'- **{qid}** [{qtype}] {q}\n')

    print(f'RAW={raw_path}')
    print(f'MD={md_path}')


if __name__ == '__main__':
    main()
