#!/bin/bash
# Maven 环境变量配置脚本

MAVEN_HOME="/Users/zhiweizhang/Downloads/aicc/apache-maven-3.9.15"
JAVA_HOME="/Users/zhiweizhang/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home"

echo "📦 Maven 环境配置信息："
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Maven Home: $MAVEN_HOME"
echo "Java Home:  $JAVA_HOME"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 检查 Maven 是否可执行
if [ -x "$MAVEN_HOME/bin/mvn" ]; then
    echo "✅ Maven 可执行文件检查通过"
else
    echo "❌ Maven 可执行文件不存在或没有执行权限"
    echo "   请运行: chmod +x $MAVEN_HOME/bin/mvn"
fi

# 检查 Java 版本
if [ -d "$JAVA_HOME" ]; then
    echo "✅ Java 17 环境检查通过"
else
    echo "❌ Java 17 环境未找到"
fi

echo ""
echo "💡 使用方法："
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "方式一：临时配置（仅当前终端有效）"
echo "   export MAVEN_HOME=$MAVEN_HOME"
echo "   export JAVA_HOME=$JAVA_HOME"
echo "   export PATH=\$MAVEN_HOME/bin:\$PATH"
echo ""
echo "方式二：永久配置（添加到 ~/.zshrc）"
echo "   echo 'export MAVEN_HOME=$MAVEN_HOME' >> ~/.zshrc"
echo "   echo 'export JAVA_HOME=$JAVA_HOME' >> ~/.zshrc"
echo "   echo 'export PATH=\$MAVEN_HOME/bin:\$PATH' >> ~/.zshrc"
echo "   source ~/.zshrc"
echo ""
echo "方式三：直接使用完整路径"
echo "   $MAVEN_HOME/bin/mvn --version"
echo "   $MAVEN_HOME/bin/mvn clean install"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 可选：创建 Maven 快捷命令
echo "是否需要创建 Maven 快捷命令？"
echo "这将在 ~/.zshrc 中添加配置（y/n）"
read -r response
if [ "$response" = "y" ] || [ "$response" = "Y" ]; then
    # 添加到 ~/.zshrc
    cat >> ~/.zshrc << 'EOF'

# Maven 3.9.15 配置
export MAVEN_HOME=/Users/zhiweizhang/Downloads/aicc/apache-maven-3.9.15
export JAVA_HOME=/Users/zhiweizhang/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home
export PATH=$MAVEN_HOME/bin:$PATH
EOF

    echo "✅ 已添加 Maven 环境配置到 ~/.zshrc"
    echo "   运行 'source ~/.zshrc' 或重启终端以生效"
else
    echo "跳过快捷命令配置"
fi
