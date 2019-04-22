FROM openjdk:8-jre

WORKDIR /opt/chain-adapter

COPY /build/libs/chain-adapter-all.jar /opt/chain-adapter/chain-adapter.jar

# Please run `kscript --package grpc_healthcheck.kts` before building docker image
COPY grpc_healthcheck /opt/chain-adapter

## Wait for script (see https://github.com/ufoscout/docker-compose-wait/)
ADD https://github.com/ufoscout/docker-compose-wait/releases/download/2.5.0/wait /opt/chain-adapter/wait
RUN chmod +x /opt/chain-adapter/wait

COPY entrypoint.sh /opt/chain-adapter/
RUN chmod +x /opt/chain-adapter/entrypoint.sh
ENTRYPOINT /opt/chain-adapter/wait && /opt/chain-adapter/entrypoint.sh
