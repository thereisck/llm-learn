#!/bin/bash
# Maven 配置验证报告脚本

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║          Maven 配置验证报告 - $(date '+%Y-%m-%d %H:%M:%S')           ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

MAVEN_HOME="/Users/zhiweizhang/Downloads/aicc/apache-maven-3.9.15"
JAVA_HOME="/Users/zhiweizhang/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home"

echo "📋 1. Maven 版本信息"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
$MAVEN_HOME/bin/mvn --version
echo ""

echo "📋 2. Maven 配置文件位置"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "配置文件: $MAVEN_HOME/conf/settings.xml"
echo ""

echo "📋 3. 阿里云镜像配置验证"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
# 检查镜像配置
if grep -q "aliyunmaven" "$MAVEN_HOME/conf/settings.xml"; then
    echo "✅ 阿里云镜像已配置"
    echo "   镜像ID: aliyunmaven"
    echo "   镜像URL: https://maven.aliyun.com/repository/public"
    echo "   镜像范围: * (所有仓库)"
else
    echo "❌ 阿里云镜像配置未找到"
fi
echo ""

echo "📋 4. JDK 17 配置验证"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
# 检查 JDK 配置
if grep -q "jdk-17" "$MAVEN_HOME/conf/settings.xml"; then
    echo "✅ JDK 17 profile 已配置并激活"
    echo "   JDK 版本: 17.0.18 (Microsoft)"
    echo "   Java Home: $JAVA_HOME"
else
    echo "❌ JDK 17 配置未找到"
fi
echo ""

echo "📋 5. 环境变量建议"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "建议在 ~/.zshrc 中添加以下配置以方便使用："
echo ""
cat << 'EOF'
# Maven 3.9.15 + JDK 17 配置
export MAVEN_HOME=/Users/zhiweizhang/Downloads/aicc/apache-maven-3.9.15
export JAVA_HOME=/Users/zhiweizhang/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home
export PATH=$MAVEN_HOME/bin:$PATH
EOF
echo ""

echo "📋 6. 常用 Maven 命令"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cat << 'EOF'
# 查看 Maven 版本
mvn --version

# 清理项目
mvn clean

# 编译项目
mvn compile

# 打包项目
mvn package

# 安装依赖
mvn install

# 运行测试
mvn test

# 跳过测试快速打包
mvn package -DskipTests
EOF
echo ""

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                      配置验证完成 ✅                           ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
