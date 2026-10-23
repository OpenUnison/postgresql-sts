# syntax=docker/dockerfile:1
ARG POSTGRES_IMAGE=ghcr.io/cloudnative-pg/postgresql:18-standard-trixie
ARG VALIDATOR_REF='1.1.0'
FROM --platform=linux/amd64 ${POSTGRES_IMAGE} AS build
USER root
ARG VALIDATOR_REF=ba9c4cb2dc9bd2e9779c7a33f7a30c9bb8c97862
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential g++-14 git ca-certificates pkg-config postgresql-server-dev-18 \
    libcurl4-openssl-dev libssl-dev \
    && rm -rf /var/lib/apt/lists/*
RUN test "$(/usr/lib/postgresql/18/bin/pg_config --version | cut -d ' ' -f 2 | cut -d . -f 1)" = 18
RUN git clone https://github.com/percona/pg_oidc_validator.git /build/validator \
    && cd /build/validator && git checkout "${VALIDATOR_REF}" \
    && git submodule update --init --recursive \
    && make USE_PGXS=1 PG_CONFIG=/usr/lib/postgresql/18/bin/pg_config \
         CXX="g++-14 -include libintl.h" -j"$(nproc)" \
    && mkdir -p /artifact \
    && cp pg_oidc_validator.so /artifact/ \
    && git rev-parse HEAD > /artifact/validator-commit.txt \
    && nm -D /artifact/pg_oidc_validator.so | grep _PG_oauth_validator_module_init
FROM --platform=linux/amd64 ${POSTGRES_IMAGE}
USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates libcurl4t64 libssl3t64 libgcc-s1 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /artifact/pg_oidc_validator.so /usr/lib/postgresql/18/lib/pg_oidc_validator.so
COPY --from=build /artifact/validator-commit.txt /usr/share/postgresql/18/validator-commit.txt
# Optional private issuer CA: put PEM .crt files in extra-ca/ before building.
COPY extra-ca/ /usr/local/share/ca-certificates/
RUN update-ca-certificates \
    && ldd /usr/lib/postgresql/18/lib/pg_oidc_validator.so > /tmp/oidc-ldd \
    && cat /tmp/oidc-ldd \
    && ! grep -q 'not found' /tmp/oidc-ldd \
    && rm /tmp/oidc-ldd
USER 26
