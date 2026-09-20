FROM nginx:1.30-alpine

# Il file di configurazione è statico: può essere copiato in fase di build, non serve un volume dedicato.
COPY infra/nginx/nginx.conf /etc/nginx/conf.d/default.conf

# Certificato dedicato di questo servizio, più la CA locale per verificare davvero
# l'identità di Tomcat quando si fa da proxy verso di lui.
COPY certs/nginx.crt /etc/nginx/certs/nginx.crt
COPY certs/nginx.key /etc/nginx/certs/nginx.key
COPY certs/ca.crt /etc/nginx/certs/ca.crt