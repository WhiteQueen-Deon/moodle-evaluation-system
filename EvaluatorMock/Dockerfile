FROM amazoncorretto:25-alpine
WORKDIR /app

# Copy Maven files
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Download dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and run
COPY src ./src

EXPOSE 8091
CMD ["./mvnw", "spring-boot:run"]