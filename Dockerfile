FROM openjdk:8-jre

WORKDIR /opt/chain-adapter

COPY /build/libs/chain-adapter-all.jar /opt/chain-adapter/chain-adapter.jar
COPY chain-adapter-run.sh /opt/chain-adapter/chain-adapter-run.sh

## Wait for script (see https://github.com/ufoscout/docker-compose-wait/)

ADD https://github.com/ufoscout/docker-compose-wait/releases/download/2.5.0/wait /opt/chain-adapter/wait
RUN chmod +x /opt/chain-adapter/wait
RUN chmod +x /opt/chain-adapter/chain-adapter-run.sh
ENTRYPOINT "/opt/chain-adapter/chain-adapter-run.sh"
