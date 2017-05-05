# Install TheHive using docker

This guide assume that you will use docker.

## How to use this image

From version 2.11, TheHive docker image doesn't come with ElasticSearch. As TheHive requires it to work, you can:
 - use docker-compose
 - manually install and configure ElasticSearch.

### Use of docker-compose

Docker-compose can start multiple dockers and link them together. It can be installed using the [documentation](https://docs.docker.com/compose/install/).
The following [docker-compose.yml](https://raw.githubusercontent.com/CERT-BDF/TheHive/master/docker/docker-compose.yml) file starts ElasticSearch, Cortex and TheHive:
```
version: "2"
services:
  elasticsearch:
    image: elasticsearch:2
    command: [
      -Des.script.inline=on,
      -Des.cluster.name=hive,
      -Des.threadpool.index.queue_size=100000,
      -Des.threadpool.search.queue_size=100000,
      -Des.threadpool.bulk.queue_size=1000]
  cortex:
    image: certbdf/cortex:latest
    ports:
      - "0.0.0.0:9001:9000"
  thehive:
    image: certbdf/thehive:latest
    depends_on:
      - elasticsearch
      - cortex
    ports:
      - "0.0.0.0:9000:9000"
```
Put this file in an empty folder and run `docker-compose up`. TheHive is exposed on 9000/tcp port and cortex on 9001/tcp. These ports
can be changed by modifying docker-compose file.

You can specify custom application.conf file by adding the line `volume: /path/to/application.conf:/etc/thehive/application.conf`
in `thehive` section.

You should define where data (ElasticSearch database) will be stored in your server by adding the line `volume: /path/to/data:/usr/share/elasticsearch/data`
in `elasticsearch` section.

### Manual installation of ElasticSearch

ElasticSearch can be installed in the same server as TheHive or not. You can then configure TheHive according to the
[documentation](Configuration.md) and run TheHive docker as follow:
```
docker run --volume /path/to/thehive/application.conf:/etc/thehive/application.conf certbdf/thehive:latest --no-config
```

You can add `--publish` docker option to expose TheHive HTTP service.

## Customize TheHive docker

By Default, TheHive docker add minimal configuration:
 - choose a random secret (play.crypto.secret)
 - search ElasticSearch instance (host named `elasticsearch`) and add it to configuration
 - search Cortex instance (host named `cortex`) and add it to configuration 

This behavious can be disabled by adding `--no-config` to docker command line: `docker run certbdf/thehive:latest --no-config`
or by adding the line `command: --no-config` in `thehive` section of docker-compose file.
 
Docker image accepts more options:
 - --no-config             : do not try to configure TheHive (add secret and elasticsearch)
 - --no-config-secret      : do not add random secret to configuration
 - --no-config-es          : do not add elasticsearch hosts to configuration
 - --es-hosts <esconfig>   : use this string to configure elasticsearch hosts (format: ["host1:9300","host2:9300"])
 - --es-hostname <host>    : resolve this hostname to find elasticseach instances
 - --secret <secret>       : secret to secure sessions
 - --cortex-proto <proto>  : define protocol to connect to Cortex (default: http)
 - --cortex-port <port>    : define port to connect to Cortex (default: 9000)
 - --cortex-url <url>      : add Cortex connection
 - --cortex-hostname <host>: resolve this hostname to find Cortex instances

 
you must install and configure ElasticSearch

Easiest way to start TheHive:
```
docker run certbdf/thehive
```