FROM adoptopenjdk:11-jre-hotspot

# Metadata
LABEL module.maintainer="DP3T" \
	  module.name="${project.artifactId}"

RUN useradd ws

COPY [ "${project.build.finalName}-exec.jar", "/home/ws/app.jar" ]
COPY [ "*.yml", "/home/ws/" ]

VOLUME [ "/tmp" ]

ENV JAVA_OPTS="$JAVA_OPTS -Xms256M -Xmx1G" \
    SERVER_PORT=8080

USER ws
EXPOSE ${SERVER_PORT}

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -Dspring.config.location=/home/ws/ -Dspring.profiles.active=${build.profile.id},jwt -jar /home/ws/app.jar" ]
