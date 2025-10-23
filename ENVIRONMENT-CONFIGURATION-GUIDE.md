# Environment Configuration Guide

## Overview

This guide explains how to configure the Auction Platform services across different environments (development, test, staging, production) using **Spring Boot Profiles** and **environment variables**.

## Table of Contents

1. [Configuration Strategy](#configuration-strategy)
2. [Available Profiles](#available-profiles)
3. [Quick Start - Local Development](#quick-start---local-development)
4. [Profile-Specific Configuration](#profile-specific-configuration)
5. [AWS Deployment (Staging/Production)](#aws-deployment-stagingproduction)
6. [Environment Variables Reference](#environment-variables-reference)
7. [Best Practices](#best-practices)

---

## Configuration Strategy

### Layered Configuration Hierarchy

Spring Boot merges configuration in this priority order (highest to lowest):

1. **Environment Variables** - Highest priority (runtime overrides)
2. **Profile-specific files** - `application-{profile}.properties`
3. **Base configuration** - `application.properties`

**Example:**
```
application.properties:           server.port=8080
application-production.properties: server.port=${PORT:8082}
Environment variable:             PORT=9000

Result: server.port=9000 (environment variable wins)
```

### Configuration Files Structure

```
item-service/src/main/resources/
├── application.properties              # Base config (all environments)
├── application-dev.properties          # Development (local Docker)
├── application-test.properties         # Integration tests
├── application-staging.properties      # AWS staging environment
└── application-production.properties   # AWS production environment

bidding-service/src/main/resources/
├── application.properties
├── application-dev.properties
├── application-test.properties
├── application-staging.properties
└── application-production.properties
```

---

## Available Profiles

| Profile | Use Case | Infrastructure | Manual Endpoints |
|---------|----------|---------------|-----------------|
| **dev** (default) | Local development | Docker Compose | ✅ Available |
| **test** | Integration tests, CI/CD | Docker or TestContainers | ✅ Available |
| **staging** | Pre-production on AWS | RDS, ElastiCache, SQS/SNS | ✅ Available |
| **production** | Live production on AWS | RDS, ElastiCache, SQS/SNS | ❌ **Disabled** |

---

## Quick Start - Local Development

### 1. Start Infrastructure

```bash
# Start PostgreSQL, RabbitMQ, Redis
docker-compose up -d

# Verify services are running
docker ps
```

### 2. Configure Services (Using Default Profile)

No configuration needed! Services run with `dev` profile by default.

```bash
# Item Service
cd item-service
mvn spring-boot:run

# Bidding Service
cd bidding-service
mvn spring-boot:run
```

**Default Behavior:**
- Uses `application.properties` + `application-dev.properties`
- Connects to local Docker services (PostgreSQL:5433, RabbitMQ:5672, Redis:6379)
- Manual auction lifecycle endpoints **enabled**
- Verbose logging (DEBUG level)

### 3. Optional: Use .env File

```bash
# Copy template
cp .env.example .env

# Edit .env (defaults are already set for development)
# No changes needed for local development

# Run with dotenv (requires dotenv tool)
cd item-service
dotenv mvn spring-boot:run
```

---

## Profile-Specific Configuration

### Development Profile (`dev`)

**Activate:**
```bash
# Method 1: Command line flag
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Method 2: Environment variable
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run

# Method 3: .env file
echo "SPRING_PROFILES_ACTIVE=dev" > .env
```

**Characteristics:**
- ✅ SQL logging enabled (`spring.jpa.show-sql=true`)
- ✅ Formatted SQL output
- ✅ DEBUG logging for application and Spring components
- ✅ Manual auction start/end endpoints available
- 🔧 Connects to local Docker services

---

### Test Profile (`test`)

**Activate:**
```bash
mvn test -Dspring.profiles.active=test
```

**Characteristics:**
- ❌ SQL logging disabled (reduce noise)
- ❌ Flyway disabled (`spring.flyway.enabled=false`)
- ❌ Scheduler disabled (`spring.task.scheduling.enabled=false`)
- ✅ Uses `create-drop` DDL for clean test state
- 🔧 Connects to `*_test` databases
- ⚡ Minimal logging (INFO/WARN only)

---

### Staging Profile (`staging`)

**Activate:**
```bash
# For JAR deployment
java -jar item-service.jar --spring.profiles.active=staging

# For containerized deployment (Dockerfile)
ENV SPRING_PROFILES_ACTIVE=staging
```

**Characteristics:**
- 🌩️ **AWS Services**: RDS PostgreSQL, ElastiCache Redis, SQS/SNS
- 🔐 **Environment Variables Required** (see [Environment Variables](#environment-variables-reference))
- ✅ Manual auction endpoints **available** (for testing)
- 📊 CloudWatch Logs integration
- 📈 CloudWatch Metrics (Prometheus + CloudWatch exporter)
- ⚙️ DEBUG logging for debugging pre-production issues
- 🔧 Hikari connection pool tuned for moderate load

**Required Environment Variables:**
```bash
DB_URL=jdbc:postgresql://staging-db.xxx.rds.amazonaws.com:5432/item_db
DB_USERNAME=auction_admin
DB_PASSWORD=<from-secrets-manager>
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=<your-access-key>
AWS_SECRET_ACCESS_KEY=<your-secret-key>
REDIS_HOST=staging-cache.xxx.cache.amazonaws.com
REDIS_PASSWORD=<redis-password>
SQS_QUEUE_BID_PLACED=https://sqs.us-east-1.amazonaws.com/.../auction-bid-placed
SNS_TOPIC_AUCTION_EVENTS=arn:aws:sns:us-east-1:...:auction-events
```

---

### Production Profile (`production`)

**Activate:**
```bash
# Kubernetes/ECS deployment
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "production"

# Docker run
docker run -e SPRING_PROFILES_ACTIVE=production item-service:latest
```

**Characteristics:**
- 🌩️ **AWS Services**: Production-grade RDS, ElastiCache, SQS/SNS
- 🔐 **All secrets from environment variables or AWS Secrets Manager**
- ❌ Manual auction endpoints **DISABLED** (`@Profile("!production")`)
- 🔒 Strict security settings (no stack traces, no SQL logging)
- ⚡ Minimal logging (WARN/ERROR only, INFO for business events)
- 📊 CloudWatch Logs + Metrics
- 🔧 Hikari connection pool tuned for high load (20-30 connections)
- 🔐 SSL/TLS enabled for Redis and RabbitMQ (if used)
- 🛡️ Actuator endpoints restricted (health + Prometheus only)

**Required Environment Variables:**
```bash
# Database
DB_URL=jdbc:postgresql://prod-db.xxx.rds.amazonaws.com:5432/item_db
DB_USERNAME=auction_admin
DB_PASSWORD=<from-secrets-manager>

# AWS
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=<use-iam-roles-instead>
AWS_SECRET_ACCESS_KEY=<use-iam-roles-instead>

# Redis
REDIS_HOST=prod-cache.xxx.cache.amazonaws.com
REDIS_PASSWORD=<redis-password>
REDIS_SSL_ENABLED=true

# SQS/SNS
SQS_QUEUE_BID_PLACED=https://sqs.us-east-1.amazonaws.com/.../prod-auction-bid-placed
SNS_TOPIC_AUCTION_EVENTS=arn:aws:sns:us-east-1:...:prod-auction-events

# CloudWatch
CLOUDWATCH_LOGS_ENABLED=true
CLOUDWATCH_LOG_GROUP=/aws/auction/item-service
```

**🔐 Production Best Practice**: Use **IAM Roles** instead of access keys
```bash
# When using IAM roles (EC2, ECS, EKS), remove these:
# AWS_ACCESS_KEY_ID=...
# AWS_SECRET_ACCESS_KEY=...

# AWS SDK will automatically use instance metadata
```

---

## AWS Deployment (Staging/Production)

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      AWS Cloud (us-east-1)                   │
│                                                               │
│  ┌───────────────┐      ┌─────────────────┐                │
│  │ Application   │      │   RDS PostgreSQL │                │
│  │ Load Balancer │──────▶   Multi-AZ       │                │
│  └───────┬───────┘      └─────────────────┘                │
│          │                                                    │
│  ┌───────▼────────────┐ ┌─────────────────┐                │
│  │ ECS/EKS Cluster    │ │ ElastiCache     │                │
│  │ ┌────────────────┐ │ │   Redis Cluster │                │
│  │ │ Item Service   ├─┼─▶   Multi-AZ      │                │
│  │ │ (3 replicas)   │ │ └─────────────────┘                │
│  │ └────────┬───────┘ │                                      │
│  │          │         │ ┌─────────────────┐                │
│  │ ┌────────▼───────┐ │ │  SQS Queues +   │                │
│  │ │ Bidding Service├─┼─▶  SNS Topics     │                │
│  │ │ (3 replicas)   │ │ └─────────────────┘                │
│  │ └────────────────┘ │                                      │
│  └────────────────────┘ ┌─────────────────┐                │
│                          │  CloudWatch     │                │
│                          │  Logs + Metrics │                │
│                          └─────────────────┘                │
└─────────────────────────────────────────────────────────────┘
```

### Deployment Steps

#### 1. Prepare AWS Infrastructure

```bash
# Create RDS PostgreSQL instance
aws rds create-db-instance \
  --db-instance-identifier auction-db-prod \
  --db-instance-class db.t3.medium \
  --engine postgres \
  --master-username auction_admin \
  --master-user-password <strong-password> \
  --allocated-storage 100 \
  --multi-az

# Create ElastiCache Redis cluster
aws elasticache create-cache-cluster \
  --cache-cluster-id auction-cache-prod \
  --cache-node-type cache.t3.medium \
  --engine redis \
  --num-cache-nodes 1 \
  --auth-token <redis-auth-token>

# Create SQS queues
aws sqs create-queue --queue-name prod-auction-bid-placed
aws sqs create-queue --queue-name prod-auction-user-outbid
aws sqs create-queue --queue-name prod-auction-started
aws sqs create-queue --queue-name prod-auction-ended
aws sqs create-queue --queue-name prod-auction-times-updated

# Create SNS topic
aws sns create-topic --name prod-auction-events
```

#### 2. Store Secrets in AWS Secrets Manager

```bash
# Create secret for Item Service
aws secretsmanager create-secret \
  --name auction/item-service/prod \
  --secret-string '{
    "DB_PASSWORD": "<rds-password>",
    "REDIS_PASSWORD": "<redis-password>",
    "AWS_ACCESS_KEY_ID": "<key-id>",
    "AWS_SECRET_ACCESS_KEY": "<secret-key>"
  }'
```

#### 3. Build Docker Image

```dockerfile
# Dockerfile (item-service/Dockerfile)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY target/item-service-0.0.1-SNAPSHOT.jar app.jar

# Use environment variables for configuration
ENV SPRING_PROFILES_ACTIVE=production

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Build and push to ECR
cd item-service
mvn clean package -DskipTests
docker build -t item-service:latest .

# Tag and push
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com
docker tag item-service:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/item-service:latest
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/item-service:latest
```

#### 4. Deploy to ECS/EKS

**ECS Task Definition (task-definition.json):**
```json
{
  "family": "item-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "item-service",
      "image": "<account-id>.dkr.ecr.us-east-1.amazonaws.com/item-service:latest",
      "portMappings": [
        {
          "containerPort": 8082,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "production"},
        {"name": "AWS_REGION", "value": "us-east-1"},
        {"name": "DB_URL", "value": "jdbc:postgresql://auction-db-prod.xxx.rds.amazonaws.com:5432/item_db"},
        {"name": "DB_USERNAME", "value": "auction_admin"},
        {"name": "REDIS_HOST", "value": "auction-cache-prod.xxx.cache.amazonaws.com"}
      ],
      "secrets": [
        {
          "name": "DB_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:xxx:secret:auction/item-service/prod:DB_PASSWORD::"
        },
        {
          "name": "REDIS_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:xxx:secret:auction/item-service/prod:REDIS_PASSWORD::"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/aws/auction/item-service",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

```bash
# Create ECS service
aws ecs create-service \
  --cluster auction-cluster \
  --service-name item-service \
  --task-definition item-service \
  --desired-count 3 \
  --launch-type FARGATE \
  --load-balancers targetGroupArn=<alb-target-group-arn>,containerName=item-service,containerPort=8082
```

---

## Environment Variables Reference

### Item Service

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | Yes | dev | Active profile (dev/test/staging/production) |
| `DB_URL` | Yes | - | JDBC connection string |
| `DB_USERNAME` | Yes | - | Database username |
| `DB_PASSWORD` | Yes | - | Database password |
| `AWS_REGION` | Staging/Prod | us-east-1 | AWS region for services |
| `AWS_ACCESS_KEY_ID` | Staging/Prod | - | AWS access key (use IAM roles instead) |
| `AWS_SECRET_ACCESS_KEY` | Staging/Prod | - | AWS secret key (use IAM roles instead) |
| `REDIS_HOST` | Yes | localhost | Redis hostname |
| `REDIS_PORT` | No | 6379 | Redis port |
| `REDIS_PASSWORD` | Staging/Prod | - | Redis authentication token |
| `REDIS_SSL_ENABLED` | Staging/Prod | false | Enable SSL for Redis |
| `SQS_QUEUE_BID_PLACED` | Prod | - | SQS queue URL for BidPlacedEvent |
| `SQS_QUEUE_AUCTION_STARTED` | Prod | - | SQS queue URL for AuctionStartedEvent |
| `SQS_QUEUE_AUCTION_ENDED` | Prod | - | SQS queue URL for AuctionEndedEvent |
| `SQS_QUEUE_AUCTION_TIMES_UPDATED` | Prod | - | SQS queue URL for AuctionTimesUpdatedEvent |
| `SNS_TOPIC_AUCTION_EVENTS` | Prod | - | SNS topic ARN for pub/sub |
| `CLOUDWATCH_LOGS_ENABLED` | No | false | Enable CloudWatch logging |
| `CLOUDWATCH_LOG_GROUP` | Staging/Prod | - | CloudWatch log group name |
| `SECRETS_MANAGER_ENABLED` | No | false | Load secrets from Secrets Manager |
| `SECRETS_MANAGER_NAME` | No | - | Secret name in Secrets Manager |

### Bidding Service

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | Yes | dev | Active profile |
| `DB_URL` | Yes | - | JDBC connection string |
| `DB_USERNAME` | Yes | - | Database username |
| `DB_PASSWORD` | Yes | - | Database password |
| `REDIS_HOST` | Yes | localhost | Redis hostname |
| `REDIS_PASSWORD` | Staging/Prod | - | Redis authentication token |
| `ITEM_SERVICE_URL` | Yes | http://localhost:8082 | Item Service base URL |
| `SQS_QUEUE_BID_PLACED` | Prod | - | SQS queue URL for publishing |
| `SQS_QUEUE_USER_OUTBID` | Prod | - | SQS queue URL for notifications |
| `SQS_QUEUE_AUCTION_STARTED` | Prod | - | SQS queue URL for consuming |
| `SQS_QUEUE_AUCTION_ENDED` | Prod | - | SQS queue URL for consuming |
| All AWS/CloudWatch variables | Same as Item Service | - | - |

### Docker Compose

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `POSTGRES_HOST_PORT` | No | 5433 | PostgreSQL exposed port |
| `POSTGRES_USER` | No | auction | PostgreSQL username |
| `POSTGRES_PASSWORD` | No | 123456 | PostgreSQL password |
| `RABBITMQ_HOST_AMQP_PORT` | No | 5672 | RabbitMQ AMQP port |
| `RABBITMQ_HOST_MANAGEMENT_PORT` | No | 15672 | RabbitMQ management UI port |
| `REDIS_HOST_PORT` | No | 6379 | Redis exposed port |

---

## Best Practices

### 1. Never Commit Secrets

```bash
# ✅ Good: Use .env files (already in .gitignore)
echo "DB_PASSWORD=super-secret" >> .env

# ❌ Bad: Hardcode in application.properties
spring.datasource.password=super-secret
```

### 2. Use Profile-Specific Defaults

```properties
# application-production.properties
# ✅ Good: Require environment variable
spring.datasource.url=${DB_URL}

# ❌ Bad: Hardcode with fallback
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/auction}
```

### 3. Validate Configuration at Startup

Add to `@Configuration` classes:

```java
@Configuration
@Profile("production")
public class ProductionConfig {

    @PostConstruct
    public void validateConfig() {
        Assert.hasText(System.getenv("DB_URL"),
            "DB_URL environment variable must be set in production");
        Assert.hasText(System.getenv("REDIS_HOST"),
            "REDIS_HOST environment variable must be set in production");
    }
}
```

### 4. Use AWS Secrets Manager in Production

```properties
# application-production.properties
aws.secretsmanager.enabled=true
aws.secretsmanager.name=auction/item-service/prod

# Spring Cloud AWS will automatically inject secrets as environment variables
```

### 5. Enable CloudWatch Logs for Observability

```properties
# application-production.properties
cloud.aws.logging.enabled=true
cloud.aws.logging.log-group=/aws/auction/item-service
logging.pattern.console=%d{ISO8601} %-5level [%thread] %logger{36} - %msg%n
```

### 6. Test Profile Configuration Before Deployment

```bash
# Test staging profile locally (use LocalStack for AWS services)
docker-compose -f docker-compose.yml -f docker-compose.localstack.yml up -d
export SPRING_PROFILES_ACTIVE=staging
export AWS_ENDPOINT=http://localhost:4566
mvn spring-boot:run
```

---

## Troubleshooting

### Issue: "Failed to configure a DataSource"

**Cause**: Missing database environment variables

**Solution**:
```bash
# Verify environment variables are set
echo $DB_URL
echo $DB_USERNAME
echo $DB_PASSWORD

# Or check in application logs
mvn spring-boot:run -Dlogging.level.org.springframework.boot.autoconfigure=DEBUG
```

### Issue: Manual endpoints return 404 in production

**Cause**: Expected behavior - endpoints are disabled by `@Profile("!production")`

**Solution**: Use staging profile for testing, or use scheduler-driven lifecycle in production

### Issue: "Could not resolve placeholder 'SQS_QUEUE_BID_PLACED'"

**Cause**: Missing AWS configuration in production/staging

**Solution**:
```bash
# Set all required SQS queue URLs
export SQS_QUEUE_BID_PLACED=https://sqs.us-east-1.amazonaws.com/.../auction-bid-placed
export SQS_QUEUE_USER_OUTBID=https://sqs.us-east-1.amazonaws.com/.../auction-user-outbid
# ... etc
```

---

## Next Steps

1. ✅ **Development**: Run with default profile (no configuration needed)
2. 📝 **Staging**: Set up AWS infrastructure and configure environment variables
3. 🚀 **Production**: Deploy with IAM roles, Secrets Manager, and CloudWatch
4. 📊 **Monitoring**: Set up CloudWatch dashboards and alarms
5. 🔐 **Security**: Rotate secrets, enable VPC endpoints, use PrivateLink

---

## Additional Resources

- [Spring Boot Profiles Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles)
- [Spring Cloud AWS Documentation](https://docs.awspring.io/spring-cloud-aws/docs/current/reference/html/index.html)
- [AWS ECS Best Practices](https://docs.aws.amazon.com/AmazonECS/latest/bestpracticesguide/intro.html)
- [Twelve-Factor App Methodology](https://12factor.net/)
