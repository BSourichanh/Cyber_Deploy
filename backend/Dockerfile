FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre

WORKDIR /app

# Créer le groupe et l'utilisateur non-root deployeur
RUN groupadd -r deployeur && useradd -r -g deployeur -d /app -s /sbin/nologin deployeur \
    && chown -R deployeur:deployeur /app

# Copier le JAR avec la propriété deployeur
COPY --from=build --chown=deployeur:deployeur /app/target/*.jar app.jar

# Basculer sous l'utilisateur deployeur
USER deployeur

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
