# Document Parsing Microservice

A high-performance, asynchronous document parsing microservice built with Spring Boot 3.2.x, Apache Tika, and PostgreSQL.

## Features

- ✅ Asynchronous document parsing with Apache Tika
- ✅ Recursive parsing for nested files (ZIP, embedded documents)
- ✅ OCR support (optional Tesseract integration)
- ✅ PostgreSQL JSONB storage with GIN indexing
- ✅ Full-Text Search (FTS) capabilities
- ✅ RESTful API with Swagger documentation
- ✅ Production-ready error handling and monitoring

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 14+ (Neon database already configured)

## Database Configuration

The application is pre-configured to connect to your Neon PostgreSQL database:
- Database: `workflow`
- Schema: `document_management`
- Connection details are in `src/main/resources/application.yml`

## Building the Application

```bash
# Clean and build
mvn clean package

# Build without tests
mvn clean package -DskipTests
```

The executable JAR will be created at: `target/document-parsing-service-1.0.0.jar`

## Running the Application

```bash
# Run the JAR
java -jar target/document-parsing-service-1.0.0.jar

# Or using Maven
mvn spring-boot:run
```

The application will start on port **8080**.

## API Endpoints

### Document Ingestion

**Upload a document:**
```bash
curl -X POST http://localhost:8080/api/v1/docs/upload \
  -F "file=@sample.pdf"
```

**Check status:**
```bash
curl http://localhost:8080/api/v1/docs/{id}/status
```

**Get document details:**
```bash
curl http://localhost:8080/api/v1/docs/{id}
```

### Search

**Search content (exact phrase):**
```bash
curl "http://localhost:8080/api/v1/search/content?query=confidential&exact=true"
```

**Search content (keywords with FTS):**
```bash
curl "http://localhost:8080/api/v1/search/content?query=report%20draft&exact=false"
```

**Search by metadata:**
```bash
curl "http://localhost:8080/api/v1/search/metadata?key=classification&value=Confidential"
```

## Swagger UI

Access the interactive API documentation at:
```
http://localhost:8080/swagger-ui.html
```

## Health Check

```bash
curl http://localhost:8080/actuator/health
```

## Application Structure

```
src/main/java/com/org/parser/
├── config/              # Configuration classes (Tika, Async)
├── controller/          # REST Controllers
├── entity/              # JPA Entities
├── repository/          # Data repositories
├── service/             # Business logic
├── exception/           # Custom exceptions and handlers
└── DocumentParserApplication.java

src/main/resources/
├── application.yml      # Application configuration
└── db/changelog/        # Liquibase migrations
```

## Key Technologies

- **Spring Boot 3.2.0** - Application framework
- **Apache Tika 2.9.1** - Document parsing
- **PostgreSQL** - Database with JSONB support
- **Liquibase** - Database migrations
- **Springdoc OpenAPI 2.3.0** - API documentation
- **Lombok** - Boilerplate reduction

## Configuration

### Thread Pool (Async Processing)
- Core Pool Size: 4
- Max Pool Size: 10
- Queue Capacity: 100

### File Upload Limits
- Max File Size: 50MB
- Max Request Size: 50MB

### Database Connection Pool
- Maximum Pool Size: 20
- Minimum Idle: 5
- Connection Timeout: 30 seconds

## Logging

Logs are configured to show:
- Application logs at DEBUG level
- Tika logs at INFO level
- SQL queries at DEBUG level

## Development

### Running in Development Mode

```bash
mvn spring-boot:run
```

### Building for Production

```bash
mvn clean package -Pprod
```

## Troubleshooting

### Database Connection Issues
- Verify the Neon database is accessible
- Check credentials in `application.yml`
- Ensure schema `document_management` exists

### Liquibase Migration Errors
If schema doesn't exist, create it manually:
```sql
CREATE SCHEMA IF NOT EXISTS document_management;
```

### Large File Processing
For files larger than 50MB, update `application.yml`:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

## Performance Tuning

### JVM Options
```bash
java -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -jar target/document-parsing-service-1.0.0.jar
```

## Support

For issues or questions, please refer to the technical documentation in `document-parsefetch.md`.
