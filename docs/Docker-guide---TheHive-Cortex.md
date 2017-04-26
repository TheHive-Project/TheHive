## How to use docker image

### Easiest way to start TheHive-Cortex:
```
docker run certbdf/thehive-cortex
```

### Exposing the port
TheHive listens on 9000/tcp, Cortex on 9001/tcp. In order to make the ports accessible add --publish parameter:
```
docker run --publish 8080:9000 --publish 8081:9001 certbdf/thehive-cortex
```
Then you can hit http://localhost:8080 or http://host-ip:8080 in your browser.

### Specify persistent data location
TheHive stores its data in /data (inside the container). You can add --volume parameter :
```
docker run --volume /path/to/persistent/data:/data certbdf/thehive-cortex
```

### Custom configuration
Default configuration is enough to start TheHive and Cortex but most analyzers require configuration. Configuration is splitted in the following files:
 * `/opt/docker/thehive.conf` main configuration file for TheHive.
 * `/opt/docker/cortex.conf` main configuration file for Cortex. By default it includes `analyzers.conf`
 * `/opt/docker/analyzers.conf` configuration for analyzers. (empty by default)

If you wish to adapt the default configuration, add a volume parameter to overwrite the configuration file:
```
docker run --volume /path/to/your/analyzers.conf:/opt/docker/conf/analyzers.conf certbdf/thehive-cortex
```

### Environment variables
This image comes with ElasticSearch and Cortex. You can disable them by adding environment one or more following variables:
 * `DISABLE_ELASTICSEARCH`
 * `DISABLE_CORTEX`

```
docker run --env DISABLE_CORTEX --env DISABLE_ELASTICSEARCH certbdf/thehive-cortex
```
Disabling ElasticSearch permits to connect to an external ElasticSearch instance. TheHive doesn't work without ElasticSearch.

The server key (`play.crypto.secret` configuration item) is used to secure session data (more details in [playframework documentation](https://www.playframework.com/documentation/2.5.x/ApplicationSecret)). If TheHive runs in cluster mode, all instance must share the same key. Docker generate a random key at startup. If you want to use your own key, you can set the variable `CRYPTO_SECRET`

```
docker run --env DISABLE_CORTEX --env CRYPTO_SECRET=JXGzd9Cyvaaupa4MqMg4fBBvRO7OegikeP7l09HDwkTEJs9vr6KNqSkzglE5wxGX certbdf/thehive-cortex
```
