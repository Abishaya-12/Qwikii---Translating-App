FROM eclipse-temurin:21-jdk

WORKDIR /app
COPY . /app

RUN javac src/Main.java

EXPOSE 8080
CMD ["java", "-cp", "src", "Main"]
