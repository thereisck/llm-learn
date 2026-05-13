#!/bin/bash
# Maven 配置应用脚本

MAVEN_CONF="/Users/zhiweizhang/Downloads/aicc/apache-maven-3.9.15/conf/settings.xml"
SOURCE_FILE="/Users/zhiweizhang/Downloads/aicc/workspace/llm-learn/settings.xml"

# 备份原配置文件
if [ -f "$MAVEN_CONF" ]; then
    echo "备份原配置文件..."
    cp "$MAVEN_CONF" "${MAVEN_CONF}.backup.$(date +%Y%m%d_%H%M%S)"
fi

# 复制新配置文件
echo "应用新配置..."
cp "$SOURCE_FILE" "$MAVEN_CONF"

if [ $? -eq 0 ]; then
    echo "✅ Maven 配置已成功应用！"
    echo ""
    echo "📌 当前配置的 Maven 信息："
    echo "   - Maven 路径: /Users/zhiweizhang/Downloads/aicc/apache-maven-3.9.15"
    echo "   - 镜像源: 阿里云公共仓库 (https://maven.aliyun.com/repository/public)"
    echo "   - Java 版本: 17"
    echo ""
    echo "💡 提示: 您可以在项目中使用以下命令测试 Maven："
    echo "   /Users/zhiweizhang/Downloads/aicc/apache-maven-3.9.15/bin/mvn --version"
else
    echo "❌ 配置应用失败，请检查权限"
fi
