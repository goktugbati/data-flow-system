# data-filter-service/Dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

RUN apt-get update && apt-get install -y dnsutils && rm -rf /var/lib/apt/lists/*
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
RUN apt-get update && apt-get install -y wget \
    && rm -rf /var/lib/apt/lists/*
RUN wget https://github.com/vi/websocat/releases/download/v1.14.0/websocat_max.aarch64-unknown-linux-musl -O /usr/local/bin/websocat \
    && chmod +x /usr/local/bin/websocat

COPY build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]