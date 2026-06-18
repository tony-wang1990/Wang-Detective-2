FROM maven:3-eclipse-temurin-21 AS builder

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    MAVEN_OPTS="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

WORKDIR /app

COPY . .

RUN mvn -Dfile.encoding=UTF-8 -Dproject.build.sourceEncoding=UTF-8 clean package -DskipTests \
    && cp target/king-detective-*.jar /app/king-detective.jar

FROM eclipse-temurin:21-jre-jammy AS base-with-tools

ENV LANG=zh_CN.UTF-8 \
    LC_ALL=zh_CN.UTF-8 \
    TZ=Asia/Shanghai

RUN apt update && \
    apt install -y --no-install-recommends openssh-client lsof curl locales && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /root/.ssh && \
    echo "Host *\n  HostKeyAlgorithms +ssh-rsa\n  PubkeyAcceptedKeyTypes +ssh-rsa" > /root/.ssh/config && \
    chmod 700 /root/.ssh && chmod 600 /root/.ssh/config && \
    locale-gen zh_CN.UTF-8 && \
    ln -fs /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

FROM base-with-tools

ARG APP_VERSION=4.1.1
ARG GIT_COMMIT=unknown
ENV KING_DETECTIVE_VERSION=${APP_VERSION} \
    KING_DETECTIVE_COMMIT=${GIT_COMMIT} \
    KING_DETECTIVE_GITHUB_REPOSITORY=tony-wang1990/Wang-Detective \
    KING_DETECTIVE_GITHUB_BRANCH=main

WORKDIR /app/king-detective

COPY --from=builder /app/king-detective.jar .

# Declare volumes for persistent data
VOLUME ["/app/king-detective/data", "/app/king-detective/keys"]

EXPOSE 9527

HEALTHCHECK --interval=30s --timeout=10s --start-period=15m --retries=5 \
    CMD curl -fsS http://127.0.0.1:9527/actuator/health | grep -q '"status":"UP"' || exit 1

CMD exec java \
    -Dfile.encoding=UTF-8 \
    -Dstdout.encoding=UTF-8 \
    -Dstderr.encoding=UTF-8 \
    --add-opens java.base/java.net=ALL-UNNAMED \
    --add-opens java.base/sun.net.www.protocol.https=ALL-UNNAMED \
    -jar king-detective.jar | tee -a /var/log/king-detective.log
