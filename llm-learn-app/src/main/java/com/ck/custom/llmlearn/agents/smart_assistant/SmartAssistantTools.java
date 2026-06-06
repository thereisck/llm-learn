package com.ck.custom.llmlearn.agents.smart_assistant;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

/**
 * 5工具定义类——对比Python手敲版，看LangChain4j的@Tool注解有多简洁
 *
 * Python版：每个工具需要手动写JSON Schema（~15行），还要append到TOOL_SCHEMAS
 * LangChain4j版：一个@Tool注解搞定，框架自动生成Schema
 */
@Slf4j
public class SmartAssistantTools {

    // ========== 白名单目录（跟Python版一致） ==========
    private static final List<String> ALLOWED_DIRS = Arrays.asList(
            "/Users/zhiweizhang/Downloads/aicc/workspace/llm-learn",
            "/tmp",
            "/private/tmp"  // macOS下/tmp实际指向/private/tmp
    );

    // ========== MySQL连接配置（跟Python版一致） ==========
    private static final String MYSQL_URL = "jdbc:mysql://localhost:3306/llm_learn?useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "llm_learn_2026";

    // ========== SQL白名单关键词（只允许SELECT和SHOW） ==========
    private static final List<String> SQL_ALLOWED_KEYWORDS = Arrays.asList("SELECT", "SHOW");
    private static final List<String> SQL_BLOCKED_KEYWORDS = Arrays.asList(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "TRUNCATE", "EXEC", "EXECUTE"
    );


    // ==================== 工具1：天气查询 ====================

    @Tool("查询指定城市的当前天气信息，包括温度、湿度、风速等。返回中文描述。")
    public String getWeather(String city) {
        log.info("🔧 调用工具: getWeather, 参数: city={}", city);
        try {
            // 用wttr.in的API（跟Python版一致）
            java.net.URL url = new java.net.URL("https://wttr.in/" + city + "?format=j1");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "curl/7.68.0");

            if (conn.getResponseCode() != 200) {
                return "❌ 天气查询失败: HTTP " + conn.getResponseCode();
            }

            String body = new String(conn.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            com.alibaba.fastjson2.JSONObject data = com.alibaba.fastjson2.JSON.parseObject(body);
            com.alibaba.fastjson2.JSONObject current = data.getJSONArray("current_condition").getJSONObject(0);

            String temp = current.getString("temp_C");
            String feelsLike = current.getString("FeelsLikeC");
            String humidity = current.getString("humidity");
            String windSpeed = current.getString("windspeedKmph");
            String desc = current.getJSONArray("weatherDesc").getJSONObject(0).getString("value");

            String result = String.format("%s当前天气: %s , 温度%s°C(体感%s°C), 湿度%s%%, 风速%skm/h",
                    city, desc, temp, feelsLike, humidity, windSpeed);
            log.info("   结果: {}", result);
            return result;

        } catch (Exception e) {
            return "❌ 天气查询失败: " + e.getMessage();
        }
    }


    // ==================== 工具2：MySQL查询 ====================

    @Tool("查询llm_learn学习数据库。只允许SELECT语句。数据库包含LLM学习相关的实验数据表。使用前请先用SHOW TABLES查看有哪些表。")
    public String queryMysql(String sql) {
        log.info("🔧 调用工具: queryMysql, 参数: sql={}", sql);

        // 安全检查：只允许SELECT和SHOW
        String upperSql = sql.trim().toUpperCase();
        for (String blocked : SQL_BLOCKED_KEYWORDS) {
            if (upperSql.startsWith(blocked)) {
                return "❌ 安全限制: 只允许SELECT和SHOW查询，不允许" + blocked + "语句";
            }
        }
        boolean isAllowed = false;
        for (String allowed : SQL_ALLOWED_KEYWORDS) {
            if (upperSql.startsWith(allowed)) {
                isAllowed = true;
                break;
            }
        }
        if (!isAllowed) {
            return "❌ 安全限制: 只允许SELECT和SHOW查询";
        }

        try {
            // 加载MySQL驱动
            Class.forName("com.mysql.cj.jdbc.Driver");

            Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
            Statement stmt = conn.createStatement();
            stmt.setMaxRows(20);  // 限制返回行数（跟Python版一致）

            ResultSet rs = stmt.executeQuery(sql);
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            // 构建结果（表头+数据，跟Python版一致）
            StringBuilder sb = new StringBuilder();
            // 表头
            for (int i = 1; i <= colCount; i++) {
                sb.append(meta.getColumnName(i)).append("\t");
            }
            sb.append("\n");

            // 数据行
            int rowCount = 0;
            while (rs.next()) {
                for (int i = 1; i <= colCount; i++) {
                    sb.append(rs.getString(i)).append("\t");
                }
                sb.append("\n");
                rowCount++;
            }

            rs.close();
            stmt.close();
            conn.close();

            String result = sb.toString().trim();
            log.info("   结果: {}行数据", rowCount);
            return result.isEmpty() ? "查询结果为空" : result;

        } catch (ClassNotFoundException e) {
            return "❌ MySQL驱动未加载，请确认pom.xml里有mysql-connector-java依赖";
        } catch (SQLException e) {
            return "❌ 数据库查询失败: " + e.getMessage();
        }
    }


    // ==================== 工具3：计算器 ====================

    @Tool("计算数学表达式。支持加减乘除、取模、幂运算。输入纯数学表达式如 '(15+27)*3/2'，不要包含文字描述。")
    public String calculate(String expression) {
        log.info("🔧 调用工具: calculate, 参数: expression={}", expression);

        // 安全检查：只允许数字、运算符、括号、空格（跟Python版一致）
        String safePattern = "[0-9+\\-*/().% \t]+";
        if (!expression.matches(safePattern)) {
            return "❌ 安全限制: 只允许纯数学表达式（数字和+-*/().%），不允许函数调用或其他字符";
        }

        try {
            // 用ScriptEngine计算（跟Python版用eval思路一致）
            javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = manager.getEngineByName("js");
            Object result = engine.eval(expression);

            String output = expression + " = " + result;
            log.info("   结果: {}", output);
            return output;

        } catch (Exception e) {
            return "❌ 计算失败: " + e.getMessage();
        }
    }


    // ==================== 工具4：文件读取 ====================

    @Tool("读取文件内容。只允许读取项目目录(llm-learn)和/tmp下的文件。")
    public String readFile(String filepath) {
        log.info("🔧 调用工具: readFile, 参数: filepath={}", filepath);

        // 安全检查：路径必须在白名单内（跟Python版一致）
        String realPath = Paths.get(filepath).toAbsolutePath().toString();
        boolean isAllowed = false;
        for (String allowedDir : ALLOWED_DIRS) {
            if (realPath.startsWith(allowedDir)) {
                isAllowed = true;
                break;
            }
        }
        if (!isAllowed) {
            return "❌ 安全限制: 只允许读取项目目录和/tmp下的文件";
        }

        try {
            String content = Files.readString(Paths.get(filepath));
            log.info("   结果: 读取{}字符", content.length());
            return content;
        } catch (IOException e) {
            return "❌ 文件读取失败: " + e.getMessage();
        }
    }


    // ==================== 工具5：文件写入 ====================

    @Tool("将内容写入文件。只允许写入项目目录(llm-learn)和/tmp下。会自动创建父目录。")
    public String writeFile(String filepath, String content) {
        log.info("🔧 调用工具: writeFile, 参数: filepath={}, content长度={}", filepath, content.length());

        // 安全检查：路径必须在白名单内（跟Python版一致）
        String realPath = Paths.get(filepath).toAbsolutePath().toString();
        boolean isAllowed = false;
        for (String allowedDir : ALLOWED_DIRS) {
            if (realPath.startsWith(allowedDir)) {
                isAllowed = true;
                break;
            }
        }
        if (!isAllowed) {
            return "❌ 安全限制: 只允许写入项目目录和/tmp下的文件";
        }

        try {
            // 自动创建父目录（跟Python版一致）
            Path path = Paths.get(filepath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content);

            String result = "✅ 写入成功: " + filepath + "，共" + content.length() + "字符";
            log.info("   结果: {}", result);
            return result;

        } catch (IOException e) {
            return "❌ 文件写入失败: " + e.getMessage();
        }
    }
}