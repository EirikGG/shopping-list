FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn -B -DskipTests compile

CMD ["mvn", "exec:java", "-Dexec.mainClass=no.eirikgg.tgg.Main"]