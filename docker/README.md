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
- Shuffle 0.7.1

## Some Hint

### docker-compose version
In docker-compose version is set 3.8, to run this version you need at least Docker Engine 19.03.0+ (check widh docker --version) and at least Docker Compose 1.25.5 (check with docker-compose --version)
```
Compose file format    Docker Engine release
3.8                    19.03.0+
3.7	               18.06.0+
3.6	               18.02.0+
3.5	               17.12.0+
3.4	               17.09.0+
```
If for some reason you have a previous version of Docker Engine or a previous version of Docker Compose and can't upgrade those, you can use 3.7 or 3.6 in docker-compose.yml


### Mapping volumes
If you take a look of docker-compose.yml you will see you need some local folder that needs to be mapped, so before do docker-compose up, ensure folders (and config files) exist:
- ./elasticsearch/data:/usr/share/elasticsearch/data
- ./elasticsearch/logs:/usr/share/elasticsearch/logs
- ./cortex/application.conf:/etc/cortex/application.conf
- ./thehive/application.conf:/etc/thehive/application.conf
- ./data:/data
- ./mysql:/var/lib/mysql

Structure would look like:
```
├── docker-compose.yml
├── elasticsearch
│   └── data
│   └── logs
├── cortex
│   └── application.conf 
└── thehive
   └── application.conf
└── data
└── mysql
```

### ElasticSearch
ElasticSearch container likes big mmap count (https://www.elastic.co/guide/en/elasticsearch/reference/current/vm-max-map-count.html) so from shell you can cgange with
```sysctl -w vm.max_map_count=262144```
Due you would run all on same system and maybe you don't have a limited amount of RAM, better to set some size, for ElasticSearch, in docker-compose.yml I added those:

```- bootstrap.memory_lock=true```
```- "ES_JAVA_OPTS=-Xms256m -Xmx256m"```

Adjust depending on your needs and your env. Without these settings in my environment ElasticSearch was using 1.5GB

### Cassandra
Like for ElasticSearch maybe you would run all on same system and maybe you don't have a limited amount of RAM, better to set some size, here for Cassandra, in docker-compose.yml I added those:

```- MAX_HEAP_SIZE=1G```
```- HEAP_NEWSIZE=1G```

Adjust depending on your needs and your env. Without these settings in my environment Cassandra was using 4GB.

### Cortex-Analyzers
- In order to use Analyzers in docker version, it is set  the online json url instead absolute path of analyzers in the application.conf of Cortex:
  https://dl.bintray.com/thehive-project/cortexneurons/analyzers.json
- In order to use Analyzers in docker version it is set the application.conf thejob: ```
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

### Cortex
- login page on 9001 port, then click "update database" and create superadmin
- as superadmin create org and other user (remember to set password) and create apikey to use for connect with the hive 

### The Hive
- In order to let The Hive reads the external application.conf and configure Cortex had to pass in command of docker compose the following option:
  --no-config
- In order to let The Hive reads the external application.conf and configure MISP for receive alerts had to pass in command of docker compose the following option:
 ```  --no-config-secret ```
- Default credentials: admin@thehive.local // secret
- In order to connect The Hive with cortex take the cortex key generated in Cortex and set it in thehive/application.conf
- MISP connection is https, in order to skip the verify of self signed certificate have do add this setting in the hive application.conf under MISP section:
  ``` wsConfig { ssl { loose { acceptAnyCertificate: true } } } ```

  
### MISP

- login with default credentials: admin@admin.test // admin
- request change password
- go in Automation page and grab the api key to use in the hive application.conf to receive alerts from MISP or to use in MISP analyzers inside Cortex.


### SHUFFLE
To test automation I choose SHUFFLE (https://shuffler.io/)

In docker-compose.yml , after the comment "#READY FOR AUTOMATION ? " there is part dedicated to Shuffle (you can remove as the others if not needed)
Here will not document how to use it, there is already documentation (https://shuffler.io/docs/about).

Here just describe how to connect the things together.

- After SHUFFLE starts, go at login page (the frontend port by default is 3001), put credentials choosen in docker-compose.yml , for your convenience I set admin // password , create your first workflow, can be anything you have in mind, then go in Triggers, place Webhook node on dashboard, select it and grab the Webhook URI. it will be something like http://192.168.29.1:3001/api/v1/hooks/webhook_0982214b-3b92-4a85-b6fa-771982c2b449
- Go in applicaiton.conf of The Hive and modify the url under webhook notification part:
```
notification.webhook.endpoints = [
  {
    name: local
    url: "http://192.168.29.1:3001/api/v1/hooks/webhook_0982214b-3b92-4a85-b6fa-771982c2b449"
    version: 0
    wsConfig: {}
    includedTheHiveOrganisations: []
    excludedTheHiveOrganisations: []
  }
]
```
- In The Hive webhooks are not enabled by default, you should enable it, there is a guide to do it: https://github.com/TheHive-Project/TheHiveDocs/blob/master/TheHive4/Administration/Webhook.md
In my case I had to call this:
```
curl -XPUT -uuser@thehive.local:user@thehive.local -H 'Content-type: application/json' 127.0.0.1:9000/api/config/organisation/notification -d '
{
  "value": [
    {
      "delegate": false,
      "trigger": { "name": "AnyEvent"},
      "notifier": { "name": "webhook", "endpoint": "local" }
    }
  ]
}'
```
- Now are able to play automation with The Hive, Cortex-Analyzers, MISP thanks to SHUFFLE!

