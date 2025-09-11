# Trip Scale Lite

A Spring Boot application for PKFare.

## Configuration Files

### application.properties

This file configures the Spring Boot application settings.

```properties
server.port=${PORT:8080}
```

The `server.port` setting configures the application to use the port specified in the `PORT` environment variable, defaulting to 8080 if not provided. This is essential for Cloud Run deployment as it dynamically assigns the port through the `PORT` environment variable.

### project.toml

This file specifies build configurations for Google Cloud Run.

```toml
[[build.env]]
name = "GOOGLE_RUNTIME_VERSION"
value = "21"
```

The `project.toml` file tells Cloud Run to use Java 21 for building and running the application. This is important because our application requires Java 21 features.

## Maven Configuration (pom.xml)

We made the following changes to the `pom.xml` file to support deployment:

1. **Added Spring Boot Maven Plugin**: This creates an executable JAR file with the proper manifest entries, required for Cloud Run to execute the application.
   ```xml
   <plugin>
     <groupId>org.springframework.boot</groupId>
     <artifactId>spring-boot-maven-plugin</artifactId>
     <version>3.2.0</version>
     <configuration>
       <mainClass>com.pkfare.trip.scale.TripScaleApplication</mainClass>
       <layout>JAR</layout>
     </configuration>
     <executions>
       <execution>
         <goals>
           <goal>repackage</goal>
         </goals>
       </execution>
     </executions>
   </plugin>
   ```

2. **Updated Jackson Dependencies**: We upgraded Jackson libraries to version 2.15.3 to ensure compatibility with Spring Boot 3.2.0.
   ```xml
   <dependency>
     <groupId>com.fasterxml.jackson.core</groupId>
     <artifactId>jackson-databind</artifactId>
     <version>2.15.3</version>
   </dependency>
   <dependency>
     <groupId>com.fasterxml.jackson.core</groupId>
     <artifactId>jackson-core</artifactId>
     <version>2.15.3</version>
   </dependency>
   <dependency>
     <groupId>com.fasterxml.jackson.core</groupId>
     <artifactId>jackson-annotations</artifactId>
     <version>2.15.3</version>
   </dependency>
   ```

## Deploying to Google Cloud Run

Follow these steps to deploy the application to Google Cloud Run:

1. **Setup GCP Project**:
   - Ensure you have a GCP project created
   - Enable the Cloud Run API

2. **Install Google Cloud CLI** (if not already installed):
   ```bash
   # Download and install based on your OS
   # https://cloud.google.com/sdk/docs/install
   ```

3. **Authenticate with GCP**:
   ```bash
   gcloud auth login
   ```

4. **Set your project ID**:
   ```bash
   gcloud config set project YOUR_PROJECT_ID
   ```

5. **Deploy the application**:
   ```bash
   # Navigate to your project directory
   cd path/to/trip-scale-lite
   
   # Deploy to Cloud Run
   gcloud run deploy --source .
   ```

6. **Access your application**:
   After deployment completes, Cloud Run will provide a URL where your application is available.

## Troubleshooting

If you encounter deployment issues, you can check the logs:

```bash
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=trip-scale-lite" --limit=20
```

Common issues include:
- Java version mismatch
- Missing dependencies
- Incorrect application configuration

For local testing before deployment:
```bash
mvn clean package
java -jar target/trip-scale-lite-1.0-SNAPSHOT.jar
```
