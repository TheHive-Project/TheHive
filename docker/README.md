## Example of docker-compose (not for production)
With this docker-compose.yml you will be able to run the following images:
- The Hive 4
- Cassandra 3.11
- Cortex 3.1.0-0.2RC1
- Elasticsearch 7.9.0
- Kibana 7.9.0
- MISP 2.4.131
- Mysql 8.0.21
- Redis 6.0.8

## Some Hint
### Cortex-Analyzers
- In order to use Analyzers in docker version, set the application.conf of Cortex the online json url instead absolute path of analyzers:
  https://dl.bintray.com/thehive-project/cortexneurons/analyzers.json
- In order to use Analyzers in docker version set the application.conf thejob: ```
  job {
  runner = [docker]
}   ```
- The analyzer in docker version will need to download from internet images, so have to add in "/etc/default/docker"
  ``` DOCKER_OPTS="--dns 8.8.8.8 --dns 1.1.1.1" ```
- When Cortex launches an analyzer need to pass the object to being analyzed, so need share /tmp folder
- When Cortex launches an analyzer it uses docker.sock, have to map in compose
 ```  /var/run/docker.sock:/var/run/docker.sock  ```
- Have to change permission on /var/run/docker.sock in order to let use socket by cortex docker and cortex-analyzers docker
  ```sudo chmod 666 /var/run/docker.sock```
- First time an analyzer/responder is executed, it will take a while because docker image is being downloaded on the fly, from second run of analyzer/responder it runs normally
- In order to let use socket both cortex image and analyzers images had to do



### The Hive
- In order to let The Hive reads the external application.conf and configure Cortex had to pass in command of docker compose the following option:
  --no-config
- In order to let The Hive reads the external application.conf and configure MISP for receive alerts had to pass in command of docker compose the following option:
 ```  --no-config-secret ```
- Default credentials: admin@thehive.local // secret
- The cortex key in application.conf must be generated  in Cortex
- MISP connection is https, in order to skip the verify of self signed certificate have do add this setting in the hive application.conf under MISP section:
  ``` wsConfig { ssl { loose { acceptAnyCertificate: true } } } ```

  
### MISP

- login with default credentials: admin@admin.test // admin
- request change password
- go in Automation page and grab the api key to use in the hive application.conf to receive alerts from MISP or to use in MISP analyzers inside Cortex.

### Cortex
- login page on 9001 port, then click "update database" and create superadmin
- as superadmin create org and other user (remember to set password) and create apikey to use in the hive application.conf



