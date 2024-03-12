FROM gcr.io/distroless/java21-debian12:latest
LABEL org.opencontainers.image.source=https://github.com/navikt/k9-vaktmester

#COPY build/libs/app.jar /app/app.jar
#WORKDIR /app
CMD [ "app.jar" ]
