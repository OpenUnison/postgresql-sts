# CloudNativePG with Percona OAuth validation

This bundle creates a NEW database cluster. It does not migrate or modify the
existing Bitnami deployment. Requires Kubernetes >=1.29, local-path provisioning,
and amd64 nodes. Operator chart 0.29.1 (CNPG 1.30.1) is bundled as a dependency.
Percona validator 1.1.0 is pinned to ba9c4cb2dc9bd2e9779c7a33f7a30c9bb8c97862.

## 1. Build and publish the amd64 database image

From this directory:

```bash
docker build --platform=linux/amd64 --progress=plain \
  -t harbor.qalab.tremolo.dev/lab/cnpg-postgresql-oidc:18-v1 .
docker push harbor.qalab.tremolo.dev/lab/cnpg-postgresql-oidc:18-v1
```

Default base: ghcr.io/cloudnative-pg/postgresql:18-standard-trixie. To pin it,
pass `--build-arg POSTGRES_IMAGE=ghcr.io/cloudnative-pg/postgresql@sha256:...`
using a verified PostgreSQL 18 standard-trixie amd64 digest. The builder installs
PGDG server headers/PGXS and GCC 14, rather than rebuilding PostgreSQL. The
compiler explicitly pre-includes libintl.h to avoid the earlier header collision.
Both stages use the same base and preserve CNPG's postgres user (UID 26).

The Dockerfile packages the library; it does not configure OAuth by itself.
If the issuer uses a private CA, add its PEM certificate as `extra-ca/issuer.crt`
before building. Do not add private keys. The client must trust that CA too.

## 2. Set deployment values

Edit chart/values.yaml:

* Set oauth.scope to the database scope actually issued in access tokens.
* Set oauth.identity to the exact token `sub` to map it to appuser, or leave it
  empty if the validated identity is already appuser.
* Optionally change oauth.authnField to another trusted identity claim.
* Set cluster.imageName to your published image (prefer an immutable digest).
* For private Harbor, supply cluster.imagePullSecrets: [{name: harbor-pull}]
  and create that docker-registry Secret in the database namespace.
* Set traefik.enabled=true if the existing Traefik instance has a postgresql
  entrypoint exposed on port 5432 and watches CRDs in this namespace.

Verify discovery from your network:

```bash
curl -fsS https://vault-sts.apps.192-168-2-115.nip.io/psql/.well-known/openid-configuration \
  | jq '{issuer,jwks_uri,device_authorization_endpoint,scopes_supported}'
```

Issuer must match exactly. Percona requires signed JWT access tokens and a
compatible JWKS document. Its 1.1.0 source validates issuer/signature/scopes, but
I did not find an audience check or configurable expected audience. Review that
limitation for your deployment; use a dedicated issuer/database scope and add
proper audience validation if isolation relies on `aud`. An identity mapping
is not a replacement for token validation. This bundle doesn't implement a new
OAuth provider, client registration, or device authorization endpoint.

## 3. Install operator, then enable the database cluster

Keep both commands on the SAME release and namespace. The first avoids creating
Cluster resources before CRDs and webhook are ready:

```bash
helm upgrade --install postgres-oauth ./chart \
  -n database --create-namespace \
  --set cluster.enabled=false --wait --timeout 5m

kubectl wait --for=condition=Established \
  crd/clusters.postgresql.cnpg.io --timeout=120s
kubectl rollout status deployment/cnpg-operator -n database --timeout=180s

helm upgrade postgres-oauth ./chart -n database \
  -f chart/values.yaml --wait --timeout 5m
kubectl wait --for=condition=Ready cluster/postgres-oauth \
  -n database --timeout=600s
```

The bundled dependency avoids needing `helm dependency update`. If an existing
CNPG operator already manages the cluster, set operator.enabled=false and skip
operator installation; do not install competing operators with the same scope.

## 4. Verify and connect

```bash
kubectl get cluster,pods,pvc,svc -n database
kubectl exec -n database postgres-oauth-1 -- \
  psql -U postgres -d appdb -c 'SHOW oauth_validator_libraries;'
kubectl exec -n database postgres-oauth-1 -- \
  psql -U postgres -d appdb -c 'TABLE pg_hba_file_rules;'
```

CNPG creates postgres-oauth-rw for primary/read-write connections. The optional
Traefik TCP route forwards to it without terminating TLS. No PostgreSQL hostPort
or Bitnami post-renderer is needed. CNPG manages database TLS certificates.
Application logins use OAuth without requiring a client certificate. CNPG still
uses its own certificates for internal replication. The explicit reject rules
prevent network password fallback; local postgres administration remains allowed.

For a client built with PostgreSQL 18 OAuth device-flow support, and only if the
issuer advertises a device authorization endpoint:

```bash
psql 'host=YOUR_TRAEFIK_ADDRESS port=5432 dbname=appdb user=appuser sslmode=require oauth_issuer=https://vault-sts.apps.192-168-2-115.nip.io/psql oauth_client_id=YOUR_REGISTERED_CLIENT_ID'
```

For server identity verification, configure a certificate covering the external
hostname and use sslmode=verify-full with the appropriate server CA. Token
exchange alone does not provide libpq's device flow; a compatible OAuth client
integration is required for non-device flows.

Storage defaults to one instance and a 10Gi local-path PVC: there is no HA or
backup configuration in this bundle. Run Helm lint/render before changing values:

```bash
helm lint ./chart -f chart/values.yaml
helm template postgres-oauth ./chart -n database -f chart/values.yaml
```

Validation performed: Helm lint, operator-only and cluster template rendering,
OAuth identity-map and optional Traefik route rendering. No live deployment,
Docker image build, or OAuth login has been verified in this environment.

Sources:
- https://github.com/cloudnative-pg/charts
- https://github.com/cloudnative-pg/postgres-containers
- https://cloudnative-pg.io/docs/1.28/postgresql_conf/
- https://github.com/percona/pg_oidc_validator/tree/1.1.0
