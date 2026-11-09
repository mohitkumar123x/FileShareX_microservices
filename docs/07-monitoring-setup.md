# Monitoring Setup - Prometheus & Grafana

Complete guide for the FileShareX monitoring stack.

---

## Architecture

```
┌──────────────┐     scrape      ┌─────────────────┐     query      ┌──────────────┐
│  Microservices│  ◄──────────── │   Prometheus     │ ◄──────────── │   Grafana     │
│  (host:port)  │  metrics/      │   (Docker)       │  PromQL       │   (Docker)    │
│               │  prometheus     │   :9090          │               │   :3000       │
└──────────────┘                 └─────────────────┘               └──────────────┘
```

- **Prometheus**: Scrapes metrics from all services every 15 seconds
- **Grafana**: Visualizes metrics with pre-built dashboards
- Both run as Docker containers on the `filesharex-monitoring` network

---

## Access URLs

| Tool | URL | Credentials |
|---|---|---|
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | None |

---

## Monitored Services

| Service | Port | Scrape URL | Status |
|---|---|---|---|
| Eureka Server | 8761 | `http://host.docker.internal:8761/actuator/prometheus` | Monitored |
| Gateway | 8080 | `http://host.docker.internal:8080/actuator/prometheus` | Monitored |
| User Service | 8081 | `http://host.docker.internal:8081/api/v1.0/actuator/prometheus` | Monitored |
| File Service | 8082 | `http://host.docker.internal:8082/api/v1.0/actuator/prometheus` | Monitored |
| Payment Service | 8083 | `http://host.docker.internal:8083/api/v1.0/actuator/prometheus` | Monitored |
| Notification Service | 8084 | `http://host.docker.internal:8084/api/v1.0/actuator/prometheus` | Monitored |
| Config Server | 9999 | N/A | Excluded (see notes) |

### Why Config Server is Excluded

Spring Cloud Config Server intercepts `/actuator/{profile}` requests, treating "prometheus" as a Spring profile name. It returns config JSON instead of Prometheus metrics. This is a known Spring Cloud limitation.

---

## Grafana Dashboard

Pre-configured dashboard at **FileShareX** folder containing:

| Panel | Description |
|---|---|
| Request Rate (per service) | Requests per second per service |
| Response Time (p95) | 95th percentile response time |
| JVM Memory Usage | Heap memory per service |
| CPU Usage | CPU utilization per service |

### Finding the Dashboard

1. Open http://localhost:3000
2. Login with **admin** / **admin**
3. Left sidebar -> **Dashboards** (grid icon) -> **Browse**
4. Open **FileShareX** folder
5. Click **FileShareX Dashboard**

---

## Prometheus

### Checking Targets

Open http://localhost:9090 -> **Status** -> **Targets**

All targets should show **UP** in green.

### Useful PromQL Queries

```promql
# Requests per second per service
sum(rate(http_server_requests_seconds_count[5m])) by (service)

# 95th percentile response time
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service))

# JVM heap memory used
jvm_memory_used_bytes{area="heap"} / 1024 / 1024

# JVM non-heap memory used
jvm_memory_used_bytes{area="nonheap"} / 1024 / 1024

# CPU usage
process_cpu_usage

# Up/down status of each service
up

# Active threads
jvm_threads_live_threads

# GC pauses
sum(rate(jvm_gc_pause_seconds_sum[5m])) by (service)
```

---

## Docker Containers

```bash
# Check monitoring containers
docker ps --filter "name=filesharex-prometheus" --filter "name=filesharex-grafana"

# Expected output:
# NAMES                   STATUS          PORTS
# filesharex-grafana      Up X minutes    0.0.0.0:3000->3000/tcp
# filesharex-prometheus   Up X minutes    0.0.0.0:9090->9090/tcp
```

---

## Configuration Files

| File | Purpose |
|---|---|
| `monitoring/prometheus/prometheus.yml` | Prometheus scrape targets configuration |
| `monitoring/grafana/provisioning/datasources/prometheus.yml` | Auto-configures Prometheus as Grafana datasource |
| `monitoring/grafana/provisioning/dashboards/dashboards.yml` | Auto-loads dashboards from the dashboards directory |
| `monitoring/grafana/dashboards/filesharex-dashboard.json` | Pre-built FileShareX dashboard |

### Grafana Datasource Config

The datasource uses `host.docker.internal:9090` to reach Prometheus from inside the container. **Do not change to `prometheus:9090`** - Docker DNS doesn't resolve on custom networks created separately.

```yaml
# monitoring/grafana/provisioning/datasources/prometheus.yml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://host.docker.internal:9090
    isDefault: true
    editable: true
```

---

## Starting Monitoring Stack

### First Time Setup

```powershell
$root = "C:\Users\mohit\Downloads\SWITCH\DEV\PROJECTS\FILE SHARE\FileShareX\fileshare_x_microservice"

# Create Docker network
docker network create filesharex-monitoring

# Start Prometheus
docker run -d --name filesharex-prometheus --network filesharex-monitoring -p 9090:9090 `
  -v "${root}/monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml" `
  prom/prometheus:latest --config.file=/etc/prometheus/prometheus.yml --storage.tsdb.retention.time=15d

# Start Grafana
docker run -d --name filesharex-grafana --network filesharex-monitoring -p 3000:3000 `
  -e GF_SECURITY_ADMIN_USER=admin -e GF_SECURITY_ADMIN_PASSWORD=admin `
  -v "${root}/monitoring/grafana/provisioning:/etc/grafana/provisioning" `
  -v "${root}/monitoring/grafana/dashboards:/var/lib/grafana/dashboards" `
  grafana/grafana:latest
```

### Restart Existing Containers

```bash
docker restart filesharex-prometheus filesharex-grafana
```

### Full Reset

```bash
docker rm -f filesharex-prometheus filesharex-grafana
# Then run the First Time Setup commands above
```

---

## Troubleshooting

### Prometheus shows all targets DOWN

1. Check that microservices are running: `Get-NetTCPConnection -LocalPort 8081,8082,8083,8084 -State Listen`
2. Check Prometheus config uses `host.docker.internal` not Docker service names
3. Restart Prometheus: `docker restart filesharex-prometheus`

### Grafana shows "No data"

1. Verify Prometheus is reachable: http://localhost:9090/targets
2. Check datasource: Grafana -> **Settings** -> **Data Sources** -> **Prometheus** -> **Save & Test**
3. Run a test query in Prometheus: `http_server_requests_seconds_count`

### Config Server metrics not available

This is expected. Spring Cloud Config Server intercepts `/actuator/prometheus` as a profile lookup. Config Server is excluded from Prometheus scraping.

---

## Endpoints Reference

### Actuator Endpoints Exposed

All application services expose these actuator endpoints:

| Endpoint | Description |
|---|---|
| `/actuator/health` | Service health status |
| `/actuator/info` | Application info |
| `/actuator/metrics` | Available metric names |
| `/actuator/prometheus` | Prometheus-format metrics |

Note: User, File, Payment, and Notification services have context path `/api/v1.0`, so endpoints are at `/api/v1.0/actuator/*`.
