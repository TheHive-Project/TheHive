# Installation guide of ElasticSearch

ElasticSearch can be installed using system package or docker. The latter is preferred as its installation and update
are easier.

## Install ElasticSearch using system package
Install the ElasticSearch package provided by Elastic:
```
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-key D88E42B4
echo "deb https://packages.elastic.co/elasticsearch/2.x/debian stable main" | sudo tee -a /etc/apt/sources.list.d/elasticsearch-2.x.list
sudo apt-get update && sudo apt-get install elasticsearch
```

The Debian package does not start up the service by default. The reason for this is to prevent the instance from
accidentally joining a cluster, without being configured appropriately.

If you prefer using ElasticSearch inside a docker, see
[ElasticSearch inside a Docker](#elasticsearch-inside-a-docker).

### ElasticSearch configuration

It is **highly recommended** to avoid exposing this service to an untrusted zone.

If ElasticSearch and TheHive run on the same host (and not in a docker), edit `/etc/elasticsearch/elasticsearch.yml` and
set `network.host` parameter with `127.0.0.1`.
TheHive use dynamic scripts to make partial updates. Hence, they must be activated using `script.inline: on`.

The cluster name must also be set ("hive" for example).

Threadpool queue size must be set with a high value (100000). The default size will get the queue easily overloaded.

Edit `/etc/elasticsearch/elasticsearch.yml` and add the following lines:

```
network.host: 127.0.0.1
script.inline: on
cluster.name: hive
threadpool.index.queue_size: 100000
threadpool.search.queue_size: 100000
threadpool.bulk.queue_size: 1000
```

### Start the Service
Now that ElasticSearch is configured, start it as a service:
```
sudo systemctl enable elasticsearch.service
sudo service elasticsearch start
```

Note that by default, the database is stored in `/var/lib/elasticsearch`.

## ElasticSearch inside a Docker

You can also start ElasticSearch inside a docker. Use the following command and do not forget to specify the absolute
path for persistent data on your host :

```
docker run \
  --publish 127.0.0.1:9200:9200 \
  --publish 127.0.0.1:9300:9300 \
  --volume /absolute/path/to/persistent/data/:/usr/share/elasticsearch/data \
  --rm \
  elasticsearch:2 \
  -Des.script.inline=on \
  -Des.cluster.name=hive \
  -Des.threadpool.index.queue_size=100000 \
  -Des.threadpool.search.queue_size=100000 \
  -Des.threadpool.bulk.queue_size=1000
```
