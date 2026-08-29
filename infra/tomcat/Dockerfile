FROM tomcat:11-jdk21-temurin-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-pip \
    && rm -rf /var/lib/apt/lists/* \
    && pip3 install --no-cache-dir \
         "psycopg2-binary~=2.9" "PyYAML~=6.0" "python-dotenv~=1.2"

ENV GRAPEHEALTH_INSTANCES=/opt/grapehealth/instances
ENV ISTANZE_ESCLUSE_FILE=/opt/grapehealth/istanze-escluse.conf

EXPOSE 8081 8082 8083 8084 8085
ENTRYPOINT ["/usr/local/bin/start-instances.sh"]