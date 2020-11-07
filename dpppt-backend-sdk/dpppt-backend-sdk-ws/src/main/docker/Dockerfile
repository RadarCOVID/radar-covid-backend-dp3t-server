FROM adoptopenjdk:11-jre-hotspot as builder
WORKDIR application
COPY [ "${project.build.finalName}-exec.jar", "application.jar" ]
RUN java -Djarmode=layertools -jar application.jar extract

FROM adoptopenjdk:11-jre-openj9

# Metadata
LABEL module.maintainer="DP3T" \
	  module.name="${project.artifactId}"

RUN useradd app

WORKDIR application

COPY --from=builder application/dependencies/ ./
COPY --from=builder application/snapshot-dependencies/ ./
COPY --from=builder application/spring-boot-loader/ ./
COPY --from=builder application/application/ ./

ARG AWS_ACCESS_KEY=dummy_access_key
ARG AWS_SECRET_KEY=dummy_secret_key
ARG AWS_PARAMSTORE_ENABLED=true

ENV AWS_ACCESS_KEY_ID ${AWS_ACCESS_KEY}
ENV AWS_SECRET_KEY ${AWS_SECRET_KEY} 
ENV AWS_PARAMSTORE_ENABLED ${AWS_PARAMSTORE_ENABLED}
		
ENV JAVA_OPTS="$JAVA_OPTS -Xms256M -Xmx1536M" \
    SERVER_PORT=8080

EXPOSE ${SERVER_PORT}

VOLUME [ "/tmp" ]

USER app

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=${build.profile.id},jwt org.springframework.boot.loader.JarLauncher" ]
