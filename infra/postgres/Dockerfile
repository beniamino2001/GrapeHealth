FROM postgres:18
COPY --chown=postgres:postgres certs/postgres.crt /var/lib/postgresql/certs/postgres.crt
COPY --chown=postgres:postgres certs/postgres.key /var/lib/postgresql/certs/postgres.key
COPY --chown=postgres:postgres certs/ca.crt /var/lib/postgresql/certs/ca.crt
RUN chmod 600 /var/lib/postgresql/certs/postgres.key