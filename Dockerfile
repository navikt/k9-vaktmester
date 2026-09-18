FROM ghcr.io/navikt/sif-baseimages/java-25:2026.09.16.0815Z
LABEL org.opencontainers.image.source=https://github.com/navikt/k9-vaktmester

COPY build/libs/app.jar /app/app.jar
WORKDIR /app
CMD [ "app.jar" ]
