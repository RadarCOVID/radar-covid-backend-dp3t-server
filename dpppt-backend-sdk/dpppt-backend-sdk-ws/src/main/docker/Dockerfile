FROM adoptopenjdk:11-jre-openj9 as builder
WORKDIR /dpppt
COPY [ "${project.build.finalName}-exec.jar", "app.jar" ]
RUN java -Djarmode=layertools -jar app.jar extract

FROM adoptopenjdk:11-jre-openj9
WORKDIR /dpppt

# Metadata
LABEL module.maintainer="DP3T" \
	  module.name="${project.artifactId}"

RUN useradd app

VOLUME [ "/tmp" ]

ARG AWS_ACCESS_KEY=dummy_access_key
ARG AWS_SECRET_KEY=dummy_secret_key
ARG AWS_PARAMSTORE_ENABLED=true

ENV AWS_ACCESS_KEY_ID ${AWS_ACCESS_KEY}
ENV AWS_SECRET_KEY ${AWS_SECRET_KEY} 
ENV AWS_PARAMSTORE_ENABLED ${AWS_PARAMSTORE_ENABLED}

ENV JAVA_TOOL_OPTIONS $JAVA_TOOL_OPTIONS -Xms512M -Xmx3072M
ENV SERVER_PORT=8080

EXPOSE ${SERVER_PORT}

USER app

COPY --from=builder dpppt/dependencies/ ./
COPY --from=builder dpppt/spring-boot-loader/ ./
COPY --from=builder dpppt/snapshot-dependencies/ ./
COPY --from=builder dpppt/company-dependencies/ ./
COPY --from=builder dpppt/application/ ./

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Dspring.profiles.active=${build.profile.id},jwt", "org.springframework.boot.loader.JarLauncher"]
