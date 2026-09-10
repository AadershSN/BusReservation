FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY src/ ./src/
COPY web/ ./web/
RUN javac -d out src/*.java
EXPOSE 8080
CMD ["java", "-cp", "out", "BusReservationSystem"]
