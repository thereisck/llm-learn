#!/bin/bash
# 设置使用指定 Maven 的环境变量

# Maven 路径
export MAVEN_HOME=/Users/zhiweizhang/Downloads/aicc/apache-maven-3.3.9-study
export PATH=$MAVEN_HOME/bin:$PATH

# Java 17 路径
export JAVA_HOME=/Users/zhiweizhang/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

echo "Maven 配置完成"
echo "Maven Home: $MAVEN_HOME"
mvn -version
echo ""
echo "Java Home: $JAVA_HOME"
java -version
