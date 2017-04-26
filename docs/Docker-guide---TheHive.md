## How to use this image
Easiest way to start TheHive:
```
docker run certbdf/thehive
```

### Exposing the port
TheHive listens on 9000/tcp. In order to make the port accessible add `--publish` parameter:
```
docker run --publish 8080:9000 certbdf/thehive
```
Then you can hit http://localhost:8080 or http://host-ip:8080 in your browser.

### Specify persistent data location
TheHive stores its data in `/data` (inside the container). You can add `--volume` parameter :
```
docker run --volume /path/to/persistent/data:/data certbdf/thehive
```
### Custom configuration
If you wish to adapt the default configuration, add a volume parameter to overwrite the configuration file:
```
docker run --volume /path/to/your/application.conf:/opt/docker/conf/application.conf certbdf/thehive
```