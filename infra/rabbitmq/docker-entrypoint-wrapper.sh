#!/usr/bin/env sh
set -eu
TEMPLATE=/etc/rabbitmq/conf.d/10-grapehealth.conf.template
OUTPUT=/etc/rabbitmq/conf.d/10-grapehealth.conf
sed \
  -e "s/__RABBITMQ_AMQP_PORT__/${RABBITMQ_AMQP_PORT}/g" \
  -e "s/__MQTT_PORT__/${MQTT_PORT}/g" \
  -e "s/__RABBITMQ_MANAGEMENT_PORT_TLS__/${RABBITMQ_MANAGEMENT_PORT_TLS}/g" \
  "$TEMPLATE" > "$OUTPUT"
exec docker-entrypoint.sh "$@"