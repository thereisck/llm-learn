# NovaRAG 系统运维手册

## 1. 服务部署

### 1.1 服务器清单

| 服务 | 服务器 | 规格 | 端口 |
|------|--------|------|------|
| NovaRAG 主服务 | rag-node-01 | 4核8G | 8900 |
| Chroma 向量库 | rag-node-02 | 8核16G | 8000 |
| Rerank 服务 | rag-node-03 | 2核4G | 8800 |
| Redis 缓存 | rag-node-04 | 2核8G | 6379 |
| MySQL 元数据库 | rag-node-05 | 4核16G | 3306 |

### 1.2 部署步骤

```bash
# 1. 拉取最新代码
cd /opt/novarag && git pull origin main

# 2. 编译打包
mvn clean package -DskipTests

# 3. 停止旧服务
./scripts/stop-all.sh

# 4. 启动依赖服务（Chroma + Redis + MySQL）
./scripts/start-infra.sh

# 5. 启动 Rerank 服务
cd rerank-service && python3 app.py &

# 6. 启动 NovaRAG 主服务
java -jar novarag-server.jar --spring.profiles.active=prod

# 7. 验证服务健康
curl http://localhost:8900/actuator/health
```

### 1.3 配置文件位置
- 主配置：`/opt/novarag/config/application-prod.yml`
- Chroma配置：`/opt/novarag/config/chroma-config.yml`
- Rerank配置：`/opt/novarag/config/rerank-config.yml`
- 日志配置：`/opt/novarag/config/logback-prod.xml`

## 2. 日志管理

### 2.1 日志级别
- 生产环境默认级别：INFO
- 调试时可临时调整为 DEBUG（需运维审批）
- 关键业务操作（查询、上传、索引）日志级别固定为 INFO，不可降低

### 2.2 日志文件
- 应用日志：`/var/log/novarag/application.log`（按日滚动，保留30天）
- 错误日志：`/var/log/novarag/error.log`（单独记录ERROR级别）
- 审计日志：`/var/log/novarag/audit.log`（记录所有数据操作，保留180天）
- Chroma日志：`/var/log/chroma/chroma.log`
- Rerank日志：`/var/log/rerank/rerank.log`

### 2.3 日志排查命令

```bash
# 查看最近100条错误日志
tail -100 /var/log/novarag/error.log

# 搜索特定用户查询日志
grep "userId=ou_xxx" /var/log/novarag/audit.log

# 统计今日查询量
grep "2026-05-21" /var/log/novarag/application.log | grep "POST /api/v1/rag/query" | wc -l
```

## 3. 监控告警

### 3.1 监控指标
- JVM内存使用率 > 85% → P1告警
- API响应时间 P99 > 3秒 → P2告警
- Chroma连接失败 → P0告警
- Rerank服务不可用 → P1告警（降级为纯向量检索）
- 每日查询量突增 > 200% → P2告警（疑似异常调用）

### 3.2 告警渠道
- P0：飞书群 + 电话通知运维负责人
- P1：飞书群通知
- P2：飞书群 + 邮件通知

### 3.3 Grafana 面板地址
http://monitoring.starcloud.internal:3000/d/novarag

## 4. 数据备份

### 4.1 Chroma 数据备份
```bash
# 每日凌晨2:00自动备份
./scripts/backup-chroma.sh

# 手动备份
curl -X POST http://localhost:8000/api/v1/backup -d '{"backup_dir": "/data/chroma-backup/2026-05-21"}'

# 恢复备份
./scripts/restore-chroma.sh /data/chroma-backup/2026-05-21
```

### 4.2 MySQL 数据备份
MySQL 每日自动全量备份（凌晨3:00），保留7天。增量备份每小时一次。

### 4.3 备份存储位置
- 本地：`/data/backups/`（SSD阵列）
- 异地：阿里云OSS（L3加密存储）

## 5. 常见故障处理

### 5.1 Chroma 不可用
**现象**：查询接口返回 "Chroma connection error"
**处理**：
1. 检查 Chroma 服务状态：`docker ps | grep chroma`
2. 检查内存使用：`free -h`（Chroma 需至少 8GB可用内存）
3. 重启 Chroma：`./scripts/restart-chroma.sh`
4. 若重启失败，从备份恢复数据

### 5.2 Rerank 服务超时
**现象**：hybrid_rerank 模式查询耗时 > 10秒
**处理**：
1. 检查 Rerank GPU 状态：`nvidia-smi`
2. 临时降级为 hybrid 模式（关闭 Rerank）
3. 重启 Rerank 服务

### 5.3 内存溢出
**现象**：java.lang.OutOfMemoryError
**处理**：
1. 增大 JVM 堆内存：`-Xmx8g`
2. 分析堆内存：`jmap -histo:live <pid>`
3. 检查是否有大文档未切分直接加载