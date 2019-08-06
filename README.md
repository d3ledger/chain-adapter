#Chain adapter
## Service overview 
Chain adapter is a service that is used to fetch Iroha blocks very safely. Unlike the default Iroha blockchain listener implementation, the service is capable of publishing missing blocks. 

The service is backed by RabbitMQ. All outgoing Iroha blocks are stored in the RabbitMQ exchange called `iroha` that fanouts all items to all bound queues. That implies that different services must use different queue names to avoid block stealing.

Chain-adapter functionality may be used by utilizing the `ReliableIrohaChainListener` class. The class and the service itself are written using Kotling programming language entirely, but it has great interoperability with Java.

The service is fail-fast, i.e it dies whenever Iroha or RabbitMQ goes offline.
## Configuration file overview
Chain adapter uses `chain-adapter.properties` as a default configuration file that is located in `resources` folder inside the project, however, every configuration item could be changed using environment variables. 

- `chain-adapter.rmqHost` - RabbitMQ host
- `chain-adapter.rmqPort` - RabbitMQ port
- `chain-adapter.irohaExchange` - exchange name that is used to publish blocks
- `chain-adapter.lastReadBlockFilePath` - the file that chain adapter uses to save last read block height. This file is needed to publish missing blocks after a restart. It's important to highlight, that blocks that have lower height values won't be published.
- `chain-adapter.healthCheckPort` - health check port
- `chain-adapter.queuesToCreate` - queues that chain adapter creates on a service startup. Queue names must be separated by the comma symbol. The value is optional.
- `chain-adapter.iroha` - Iroha host and port configuration
- `chain-adapter.dropLastReadBlock` - as it was mentioned before, chain adapter saves last read block height. It's possible to drop height to zero on a service startup by setting this value to `true`. Good for testing purposes. 
- `chain-adapter.irohaCredential` - credentials of an account that will be used by the service to listen to Iroha blocks. The account must have `can_get_blocks` permission.

## How to run
Chain-adapter may be run as a docker container using the following `docker-compose` instructions:

```rmq:
  image: rabbitmq:3-management
  container_name: rmq
  ports:
    - 8181:15672
    - 5672:5672

chain-adapter:
  image: nexus.iroha.tech:19002/d3-deploy/chain-adapter:develop
  container_name: chain-adapter
  restart: on-failure
  depends_on:
    - iroha
    - rmq
  volumes:
    - ../your change adapter storage/:/deploy/chain-adapter
  ```