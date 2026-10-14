FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN mvn -B -ntp -pl services/${MODULE} -am package -DskipTests && \
    cp services/${MODULE}/target/${MODULE}-1.0.0-SNAPSHOT.jar /tmp/app.jar
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --uid 10001 --create-home erpuser
COPY --from=build /tmp/app.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
