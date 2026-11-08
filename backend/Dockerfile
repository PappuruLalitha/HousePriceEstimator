
FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY CostCalculatorServer.java .

RUN javac -d out CostCalculatorServer.java

EXPOSE 8081

CMD ["sh", "-c", "java -cp out CostCalculatorServer"]
