FROM ghcr.io/navikt/sif-baseimages/java-chainguard-25:2026.11.23.1517Z
LABEL org.opencontainers.image.source=https://github.com/navikt/k9-vaktmester

WORKDIR /app
COPY build/libs/app.jar app.jar
