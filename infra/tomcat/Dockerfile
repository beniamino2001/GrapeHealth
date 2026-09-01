# Questa immagine Maven serve solo a prelevare un'installazione Maven gia' pronta e
# verificata, non per eseguire qui alcuna build: la compilazione dei quattro moduli
# avviene a runtime, dentro start-instances.sh, per poter ricompilare codice aggiornato
# a ogni "docker compose down" senza bisogno anche di un "--build" dell'immagine.
FROM maven:3.9-eclipse-temurin-21 AS maven-tools

FROM tomcat:11-jdk21-temurin-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-pip \
    && rm -rf /var/lib/apt/lists/* \
    && pip3 install --no-cache-dir \
         "psycopg2-binary~=2.9" "PyYAML~=6.0" "python-dotenv~=1.2"

# Stessa installazione Maven di cui sopra, solo prelevata invece che eseguita in questa fase.
COPY --from=maven-tools /usr/share/maven /usr/share/maven
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV GRAPEHEALTH_INSTANCES=/opt/grapehealth/instances
ENV ISTANZE_ESCLUSE_FILE=/opt/grapehealth/istanze-escluse.conf

# Codice sorgente dei quattro moduli Spring Boot: compilato a runtime da start-instances.sh
# a ogni container ricreato da zero, non qui. target/ e' escluso dal contesto di build da
# .dockerignore: qualunque build locale di chi ha clonato il repository non interferisce.
COPY backend     /opt/grapehealth/src/backend
COPY api         /opt/grapehealth/src/api
COPY attuatori   /opt/grapehealth/src/attuatori
COPY persistence /opt/grapehealth/src/persistence

# La dashboard non ha un WAR: e' servita come file statici. package.json e *.test.js
# servono solo a "node --test" in locale, mai all'istanza Tomcat che la serve davvero.
COPY dashboard /opt/grapehealth/dist/dashboard-src
RUN rm -f /opt/grapehealth/dist/dashboard-src/package.json \
    && rm -f /opt/grapehealth/dist/dashboard-src/js/*.test.js

COPY infra/tomcat/server.xml.template /opt/grapehealth/templates/server.xml.template
COPY infra/tomcat/setenv.sh.template  /opt/grapehealth/templates/setenv.sh.template

EXPOSE 8081 8082 8083 8084 8085
ENTRYPOINT ["/usr/local/bin/start-instances.sh"]