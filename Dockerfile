# Declare Source Digest for the Base Image
FROM gematik1/osadl-alpine-openjdk21-jre:1.0.6@sha256:4f1280cd30c9ee1242656106fb880c9d66b517657932037f2ee79cbd20cb7623

# The STOPSIGNAL instruction sets the system call signal that will be sent to the container to exit
# SIGTERM = 15 - https://de.wikipedia.org/wiki/Signal_(Unix)
STOPSIGNAL SIGTERM

# Define the exposed port or range of ports for the service
EXPOSE 8080

# Defining Healthcheck
HEALTHCHECK --interval=15s \
            --timeout=10s \
            --start-period=30s \
            --retries=3 \
            CMD ["/usr/bin/wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]

# Default USERID and GROUPID
ARG USERID=10000
ARG GROUPID=10000

# Copy the Application WAR
COPY --chown=$USERID:$GROUPID target/isik-mock-server.war /app/app.war
# Copy the custom Web UI Resources
COPY --chown=$USERID:$GROUPID custom /app/custom

# Run as User (not root)
USER $USERID:$USERID

WORKDIR /app

ENTRYPOINT ["java", "--class-path", "./app.war", "-Dloader.path=app.war!/WEB-INF/classes/,app.war!/WEB-INF/,/app/extra-classes", "org.springframework.boot.loader.PropertiesLauncher"]

# Git Args
ARG COMMIT_HASH
ARG VERSION

###########################
# Labels
###########################
LABEL de.gematik.vendor="gematik GmbH" \
      maintainer="software-development@gematik.de" \
      de.gematik.app="ISiK Mock Server" \
      de.gematik.git-repo-name="https://github.com/gematik/isik-mock-server " \
      de.gematik.commit-sha=$COMMIT_HASH \
      de.gematik.version=$VERSION
