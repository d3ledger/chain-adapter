#!/usr/bin/env bash

if  [ -z "${IROHA_HOST}" ] || [ -z "${IROHA_PORT}" ]; then
    echo "Please set IROHA_HOST and IROHA_PORT variables"
    exit
fi

STATE=1

while [ $STATE -ne 0 ]; do
    ./grpc_healthcheck ${IROHA_HOST} ${IROHA_PORT}
    STATE=$?
    echo "Waiting for iroha on ${IROHA_HOST}:${IROHA_PORT}"
    sleep 0.1
done

java -jar /opt/chain-adapter/chain-adapter.jar
