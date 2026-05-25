# Design-to-Code AI Pipeline - Operations Guide

## Overview

This guide is for operations teams responsible for deploying, monitoring, and maintaining the Design-to-Code AI Pipeline platform.

## Table of Contents

1. [Deployment](#deployment)
2. [Monitoring](#monitoring)
3. [Backup and Recovery](#backup-and-recovery)
4. [Security](#security)
5. [Troubleshooting](#troubleshooting)

## Deployment

### Prerequisites

- Kubernetes cluster (recommended) or Docker runtime
- GitHub Actions runner or equivalent CI/CD system
- Ollama service (local or cloud)
- PostgreSQL or MongoDB (for Testcontainers)
- Sufficient resources: 4 CPU, 8GB RAM minimum per pipeline run

### Deployment Options

#### Option 1: GitHub Actions (Recommended)

The pipeline is designed to run in GitHub Actions containers.

**Workflow Configuration:**

```yaml
name: Design-to-Code Pipeline

on:
  push:
    branches:
      - design/**

jobs:
  pipeline:
    runs-on: ubuntu-latest
    container:
      image: openjdk:21-jdk
    steps:
      - uses: actions/checkout@v3
      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2
      - name: Run Pipeline
        run: ./gradlew run --args="--workspace=$GITHUB_WORKSPACE --changedFiles=$(git diff --name-only ${{ github.event.before }} ${{ github.sha }})"
```

#### Option 2: Kubernetes Deployment

Deploy the pipeline as a Kubernetes Job:

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: design-to-code-pipeline
spec:
  template:
    spec:
      containers:
      - name: pipeline
        image: design-to-code:latest
        env:
        - name: OLLAMA_HOST
          value: "ollama-service"
        - name: OLLAMA_PORT
          value: "11434"
        - name: GITHUB_TOKEN
          valueFrom:
            secretKeyRef:
              name: github-secrets
              key: token
        resources:
          requests:
            memory: "4Gi"
            cpu: "2"
          limits:
            memory: "8Gi"
            cpu: "4"
      restartPolicy: OnFailure
```

#### Option 3: Docker Compose

For local or simple deployments:

```yaml
version: '3.8'
services:
  pipeline:
    build: .
    environment:
      - OLLAMA_HOST=ollama
      - OLLAMA_PORT=11434
    volumes:
      - ./workspace:/workspace
    depends_on:
      - ollama
  
  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama

volumes:
  ollama_data:
```

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `OLLAMA_HOST` | Ollama API host | localhost | Yes |
| `OLLAMA_PORT` | Ollama API port | 11434 | Yes |
| `OLLAMA_MODEL` | Model to use | codellama:13b | Yes |
| `GITHUB_TOKEN` | GitHub API token | - | No |
| `COVERAGE_THRESHOLD` | Coverage threshold | 100 | No |
| `BRANCH_PREFIX` | Branch prefix | feature/ai-gen | No |

### Configuration Management

Store configuration in ConfigMaps (Kubernetes) or environment files:

```bash
# config.env
OLLAMA_HOST=ollama-service
OLLAMA_PORT=11434
OLLAMA_MODEL=codellama:13b
COVERAGE_THRESHOLD=100
BRANCH_PREFIX=feature/ai-gen
```

Load configuration:
```bash
export $(cat config.env | xargs)
```

## Monitoring

### Metrics Collection

The pipeline collects metrics automatically:

- **Execution Time**: Total pipeline duration and per-stage duration
- **Success Rate**: Percentage of successful pipeline runs
- **Stage Status**: Success/failure status for each stage
- **Resource Usage**: CPU and memory consumption

### Viewing Metrics

#### Export Metrics to File

```kotlin
val collector = MetricsCollector()
collector.exportMetricsToFile("/var/log/pipeline-metrics.txt")
```

#### Metrics Format

```
=== Pipeline Metrics Export ===
Total Pipelines: 10
Overall Success Rate: 90%

Pipeline ID: exec-123
  Start Time: 2026-05-24T23:00:00Z
  End Time: 2026-05-24T23:05:00Z
  Duration: 300s
  Overall Success: true
  Stage Success Rate: 100%
  Stages:
    - Context Analysis: ✅ (5s)
    - Prompt Construction: ✅ (3s)
    - AI Generation: ✅ (250s)
    - Quality Gate Validation: ✅ (30s)
    - Git Operations: ✅ (12s)
```

### Logging

Logs are structured with correlation IDs for tracing:

```
2026-05-24 23:00:00.000 INFO [requestId=abc-123, workspace=/path, model=codellama] === Design-to-Code AI Pipeline Started ===
2026-05-24 23:00:05.000 INFO [requestId=abc-123] [Stage 1] Context Analysis & Detection
2026-05-24 23:00:10.000 INFO [requestId=abc-123] Detected: Spring Boot, PostgreSQL
```

### Log Levels

Configure log levels in `logback.xml`:

```xml
<configuration>
    <logger name="com.designtocode" level="INFO"/>
    <logger name="com.designtocode.cli" level="DEBUG"/>
    <root level="WARN"/>
</configuration>
```

### Alerting

Set up alerts for:

- **Pipeline Failure Rate**: Alert if failure rate > 10%
- **Pipeline Duration**: Alert if duration > 15 minutes
- **Quality Gate Failures**: Alert if coverage < 100%
- **Ollama Connection**: Alert if Ollama is unavailable

Example Prometheus alerting rules:

```yaml
groups:
- name: pipeline_alerts
  rules:
  - alert: HighFailureRate
    expr: pipeline_failure_rate > 0.1
    for: 5m
    annotations:
      summary: "High pipeline failure rate"
  - alert: LongPipelineDuration
    expr: pipeline_duration_seconds > 900
    annotations:
      summary: "Pipeline taking too long"
```

### Health Checks

Implement health check endpoint:

```kotlin
@GetMapping("/health")
fun health(): Health {
    return if (isOllamaHealthy() && isDockerHealthy()) {
        Health.up().build()
    } else {
        Health.down().build()
    }
}
```

## Backup and Recovery

### What to Backup

1. **Configuration Files**: `pipeline-config.yaml`
2. **Design Specifications**: Files in `openapi/` and `docs/spec/`
3. **Generated Code**: Feature branches with generated code
4. **Metrics History**: Exported metrics files
5. **Logs**: Pipeline execution logs

### Backup Strategy

#### Automated Backups

Use GitHub Actions to backup specifications:

```yaml
- name: Backup Specifications
  run: |
    tar -czf specs-backup-$(date +%Y%m%d).tar.gz openapi/ docs/spec/
    aws s3 cp specs-backup-*.tar.gz s3://backup-bucket/
```

#### Manual Backup

```bash
# Backup specifications
tar -czf specs-backup.tar.gz openapi/ docs/spec/

# Backup configuration
cp pipeline-config.yaml config-backup.yaml

# Export metrics
./gradlew run --args="--export-metrics"
```

### Recovery Procedure

1. **Restore Specifications**:
```bash
tar -xzf specs-backup.tar.gz
```

2. **Restore Configuration**:
```bash
cp config-backup.yaml pipeline-config.yaml
```

3. **Restore Generated Code**:
```bash
git checkout feature/ai-gen-<sha>
```

4. **Verify Restoration**:
```bash
./gradlew build
./gradlew test
```

## Security

### Secrets Management

Never commit secrets to the repository. Use:

#### GitHub Secrets

Store in GitHub repository settings:
- `GITHUB_TOKEN`
- `OLLAMA_API_KEY` (if using cloud Ollama)
- `DATABASE_URL` (if needed)

#### Kubernetes Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: pipeline-secrets
type: Opaque
stringData:
  github-token: "ghp_xxx"
  ollama-api-key: "sk-xxx"
```

#### Environment Variables

Load from secure environment:
```bash
export GITHUB_TOKEN=$(vault read -field=token secret/pipeline/github)
```

### Container Security

- Use official base images
- Scan images for vulnerabilities: `trivy image design-to-code:latest`
- Run as non-root user
- Limit container capabilities
- Use resource limits

### Network Security

- Use internal network for Ollama communication
- Restrict GitHub API access
- Implement rate limiting
- Use TLS for external communications

### Code Security

- Sanitize AI-generated code
- Run security scans: `./gradlew dependencyCheckAnalyze`
- Review generated code before merging
- Implement code signing

### Access Control

- Restrict who can trigger the pipeline
- Use branch protection rules
- Require PR review for merges
- Audit pipeline executions

## Troubleshooting

### Common Operational Issues

#### Pipeline Stuck in Running State

**Symptoms**: Pipeline doesn't complete, no progress logs

**Diagnosis**:
1. Check container status: `docker ps`
2. Check resource usage: `docker stats`
3. Check logs: `docker logs <container-id>`

**Solutions**:
- Increase resource limits
- Kill stuck container: `docker kill <container-id>`
- Restart pipeline

#### Ollama Service Unavailable

**Symptoms**: "Ollama connection error" in logs

**Diagnosis**:
```bash
curl http://ollama:11434/api/tags
```

**Solutions**:
- Restart Ollama: `docker restart ollama`
- Check Ollama logs: `docker logs ollama`
- Verify network connectivity

#### Quality Gate Fails Consistently

**Symptoms**: Pipeline always fails at quality gate stage

**Diagnosis**:
- Check coverage report: `build/reports/kover/html/index.html`
- Check Detekt report: `build/reports/detekt/`

**Solutions**:
- Adjust coverage threshold if appropriate
- Fix linting issues in generated code
- Add missing tests manually

#### High Resource Usage

**Symptoms**: Pipeline consumes excessive CPU/memory

**Diagnosis**:
```bash
docker stats
top
```

**Solutions**:
- Set resource limits in container configuration
- Optimize AI model selection
- Implement caching
- Scale horizontally

### Log Analysis

#### Enable Debug Logging

```xml
<logger name="com.designtocode" level="DEBUG"/>
```

#### Search Logs by Correlation ID

```bash
grep "requestId=abc-123" pipeline.log
```

#### Analyze Failed Pipelines

```bash
grep "Pipeline Failed" pipeline.log | tail -20
```

### Performance Tuning

#### Optimize AI Generation

- Use smaller models for faster generation
- Implement prompt caching
- Batch multiple specifications

#### Optimize Quality Gates

- Run tests in parallel
- Use incremental compilation
- Cache test results

#### Optimize Git Operations

- Use shallow clones
- Implement branch caching
- Optimize PR creation

### Disaster Recovery

#### Pipeline Repository Corrupted

1. Restore from backup
2. Verify integrity: `./gradlew build`
3. Test with sample specification
4. Monitor next pipeline runs

#### Ollama Model Corrupted

1. Re-download model: `ollama pull codellama:13b`
2. Verify model: `ollama list`
3. Test generation
4. Monitor pipeline

#### GitHub Token Expired

1. Generate new token
2. Update secrets
3. Test authentication: `gh auth status`
4. Trigger test pipeline

## Maintenance

### Regular Tasks

- **Daily**: Monitor pipeline success rate
- **Weekly**: Review metrics and logs
- **Monthly**: Update dependencies
- **Quarterly**: Review and update documentation

### Dependency Updates

```bash
# Check for updates
./gradlew dependencyUpdates

# Update dependencies
./gradlew build --refresh-dependencies
```

### Health Checks

Run periodic health checks:

```bash
# Check Ollama
curl http://ollama:11434/api/tags

# Check Docker
docker ps

# Check disk space
df -h
```

## Support

### Escalation Path

1. **Level 1**: Check logs and common issues
2. **Level 2**: Review metrics and configuration
3. **Level 3**: Contact development team

### Contact Information

- **Development Team**: dev-team@example.com
- **On-Call**: on-call@example.com
- **Slack**: #pipeline-operations

### Resources

- [User Guide](user-guide.md)
- [Developer Guide](developer-guide.md)
- [GitHub Repository](https://github.com/soraiayugulis/design-to-code)
- [Issue Tracker](https://github.com/soraiayugulis/design-to-code/issues)
