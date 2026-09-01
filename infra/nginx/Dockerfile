FROM nginx:1.30-alpine

# Il file di configurazione è statico (nessun segreto, nessuna interpolazione a runtime
# come server.xml.template): può essere copiato in fase di build, non serve un volume.
# Sovrascrive il default.conf del progetto ufficiale, che altrimenti confliggerebbe.
COPY infra/nginx/nginx.conf /etc/nginx/conf.d/default.conf

# Certificato dedicato di questo servizio, più la CA locale per verificare davvero
# l'identità di Tomcat quando si fa da proxy verso di lui (non solo cifrare il canale).
COPY certs/nginx.crt /etc/nginx/certs/nginx.crt
COPY certs/nginx.key /etc/nginx/certs/nginx.key
COPY certs/ca.crt /etc/nginx/certs/ca.crt