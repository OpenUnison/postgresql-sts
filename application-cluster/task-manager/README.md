# Taskboard — Java task manager

Java 21 / Spring Boot 3.5.16 / JDBC / PostgreSQL. No web authentication.
Features: create, edit, delete, mark done, three statuses, optional due date,
search and status filters. The frontend is served by the Java application;
no Node build or external frontend CDN is needed.

## Existing database

Provide a database and role with CONNECT, schema USAGE/CREATE, and permissions
to read/write the application's table and identity sequence. At startup the app
runs src/main/resources/schema.sql: CREATE TABLE IF NOT EXISTS task_manager_tasks.
It does not create/drop a database or modify other tables. This is initial schema
setup, not a versioned migration system. Alternatively have a DBA run schema.sql
and set DB_INIT_MODE=never, then grant the application role table/sequence access.

Environment:
- DB_URL: JDBC URL, e.g. jdbc:postgresql://postgres-oauth-rw.database.svc.cluster.local:5432/appdb?sslmode=require
- DB_USERNAME: database login, e.g. appuser
- DB_PASSWORD: database password (required)
- DB_INIT_MODE: always (default) or never

The app uses standard JDBC password authentication. For the earlier CNPG cluster,
ensure a matching scram-sha-256 HBA rule comes BEFORE an OAuth rule/reject rule.
There is no automatic OAuth-to-password fallback. Database credentials are separate
from web authentication: the website has no login and all visitors share tasks.

## Local run

Install Maven 3.6.3+ and JDK 21:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/appdb?sslmode=require'
export DB_USERNAME='appuser'
read -r -s DB_PASSWORD; export DB_PASSWORD
mvn spring-boot:run
```

Open http://localhost:8080. For plaintext local database testing only, use a JDBC
URL without sslmode=require. For production identity verification, use
sslmode=verify-full with a matching server name and trusted CA configured for JDBC.

## Build a JAR and Docker-compatible amd64 image

```bash
mvn clean package
```

Outputs:
- target/task-manager-1.0.0.jar (executable Spring Boot JAR)
- target/jib-image.tar (linux/amd64 image, no Docker daemon needed)

```bash
docker load -i target/jib-image.tar
docker tag task-manager:1.0.0 harbor.qalab.tremolo.dev/lab/task-manager:1.0.0
docker push harbor.qalab.tremolo.dev/lab/task-manager:1.0.0
```

You can set the image tag while building:

```bash
mvn package -Dimage.name=harbor.qalab.tremolo.dev/lab/task-manager:1.0.0
```

For direct registry publication with configured registry credentials:

```bash
mvn compile jib:build -Dimage.name=harbor.qalab.tremolo.dev/lab/task-manager:1.0.0
```

Skip image creation: `mvn package -Djib.skip=true`.
If global cache writes are restricted: `mvn package -Djib.useOnlyProjectCache=true`.
An alternative multi-stage Dockerfile is included:
`docker build --platform=linux/amd64 -t task-manager:1.0.0 .`

## Kubernetes

1. Edit k8s/configmap.yaml to use your existing PostgreSQL JDBC URL.
2. Edit the image in k8s/deployment.yaml to match the pushed tag.
3. Create the database credential Secret in the app namespace:

```bash
kubectl apply -f k8s/namespace.yaml
# Copy and edit the example; don't commit actual credentials.
cp k8s/secret.example.yaml k8s/secret.yaml
# Replace DB_USERNAME / DB_PASSWORD in k8s/secret.yaml.
kubectl apply -f k8s/secret.yaml
kubectl apply -k k8s
kubectl rollout status deployment/task-manager -n task-manager
kubectl port-forward -n task-manager svc/task-manager 8080:80
```

Open http://localhost:8080. An optional Traefik Ingress example is provided;
replace its hostname and apply separately. No Ingress is exposed by default.
If Harbor needs authentication, create a registry pull Secret in task-manager
and enable imagePullSecrets in the Deployment. The prebuilt image is amd64;
use amd64 nodes or change Jib's platform and rebuild for your architecture.
After changing DB ConfigMap/Secret values, restart the Deployment to reload them.
No PostgreSQL deployment or PVC is included because this uses your existing DB.

The Deployment runs as non-root with a read-only root filesystem, writable /tmp,
automountServiceAccountToken=false, database-aware readiness and process-only
liveness. Anyone who can reach the app can change/delete tasks, as requested.

## Verification

```bash
mvn test
python3 scripts/smoke-test.py http://localhost:8080
```

The smoke test creates a uniquely named task, checks persistence through the API,
updates/deletes it, checks invalid input and verifies database readiness. It cleans
up its test task and never clears existing tasks.

Verified during generation: Maven compilation and four MVC tests; creation of the
amd64 image archive via Jib; JavaScript syntax; Kustomize rendering. A local
PostgreSQL integration run was blocked by the environment's shared-memory
restriction, so real-database smoke testing and container/Kubernetes runtime
verification remain to be run. No existing cluster or database was changed.
