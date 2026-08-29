FROM rabbitmq:4.3-management

COPY infra/rabbitmq/docker-entrypoint-wrapper.sh /usr/local/bin/docker-entrypoint-wrapper.sh
RUN chmod +x /usr/local/bin/docker-entrypoint-wrapper.sh
ENTRYPOINT ["/usr/local/bin/docker-entrypoint-wrapper.sh"]
CMD ["rabbitmq-server"]

COPY --chown=rabbitmq:rabbitmq certs/rabbitmq.crt /etc/rabbitmq/certs/rabbitmq.crt
COPY --chown=rabbitmq:rabbitmq certs/rabbitmq.key /etc/rabbitmq/certs/rabbitmq.key
COPY --chown=rabbitmq:rabbitmq certs/ca.crt /etc/rabbitmq/certs/ca.crt
RUN chmod 600 /etc/rabbitmq/certs/rabbitmq.key