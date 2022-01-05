FROM navikt/java:17
LABEL org.opencontainers.image.source=https://github.com/navikt/k9-vaktmester
COPY build/libs/app.jar app.jar
