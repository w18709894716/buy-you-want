# syntax=docker/dockerfile:1.6
#
# 通用服务镜像：一次编写，所有后端服务复用。
#   docker build -f deploy/Dockerfile --build-arg MODULE=byw-gateway -t byw/byw-gateway:local .
#
# 说明：
# - 构建阶段用 maven 官方镜像离线打包（-pl MODULE -am 连同 byw-common / byw-api 一起编译）；
# - 运行阶段只放一个 fat jar，非 root 用户运行，堆内存按容器限额自动计算；
# - 上下文用仓库根目录（见 .dockerignore：排除 node_modules/target/docs，避免把整个仓库塞进构建上下文）。
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /workspace
# 复制整个后端源码：模块间的 pom 依赖关系复杂，逐一定制 COPY 收益不大且易漏新模块
COPY . .
RUN mvn -B -ntp -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:17-jre
ARG MODULE
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Duser.timezone=Asia/Shanghai"
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/*.jar /app/app.jar
# 非 root 运行
RUN useradd -r -u 1001 byw && chown -R byw:byw /app
USER byw
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
