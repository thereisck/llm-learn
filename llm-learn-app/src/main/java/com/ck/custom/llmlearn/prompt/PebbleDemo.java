package com.ck.custom.llmlearn.prompt;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 如果需要复杂逻辑（循环、条件、继承），Pebble更合适
 * @author changkong
 * @date 2026/4/29 22:09
 **/
public class PebbleDemo {
    public static void main(String[] args) throws IOException {

        //创建引擎（类似 Jinja2 Environment）
        PebbleEngine engine = new PebbleEngine.Builder().build();

        //加载模版（可以从文件、字符串等加载）
        PebbleTemplate template = engine.getTemplate("templates/code_review.html");

        //准备变量
        Map<String, Object> context = new HashMap<>();
        context.put("reviewer", "安全专家");
        context.put("issues", List.of(
                Map.of("type", "性能", "severity", "高", "description", "未处理的异常情况"),
                Map.of("type", "安全", "severity", "中", "description","未优化的代码")
        ));
        context.put("project_name", "MyProject");
        context.put("review_date", "2026-04-29");
        context.put("high_risk_count", 1);

        //渲染模版
        StringWriter writer = new StringWriter();
        template.evaluate(writer, context);
        System.out.println(writer.toString());
    }
}
