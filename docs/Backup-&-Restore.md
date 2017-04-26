# Backup and restore data
All persistent data are stored in ElasticSearch database. The backup and restore procedures are the ones that are detailed in [ElasticSearch documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-snapshots.html).

_Note_: you may have to adapt you indices in the examples below. To find the right indice, use the following command :

```
curl 'localhost:9200/_cat/indices?v'
```

## 1. Create a snapshot repository
First you must define a location in local filesystem (where ElasticSearch instance runs) where the backup will be written. Be careful if you run ElasticSearch in Docker, the directory must be mapped in host filesystem using `--volume` parameter (cf. [Docker documentation](https://docs.docker.com/engine/tutorials/dockervolumes/)).
Create a ElasticSearch snapshot point with the following command :
```
$ curl -XPUT 'http://localhost:9200/_snapshot/the_hive_backup' -d '{
    "type": "fs",
    "settings": {
        "location": "/absolute/path/to/backup/directory",
        "compress": true
    }
}'
```

## 2. Backup your data
Start the backup by executing the following command :
```
$ curl -XPUT 'http://localhost:9200/_snapshot/the_hive_backup/snapshot_1' -d '{
  "indices": "the_hive_8"
}'
```
You can backup the last index of TheHive (you can list indices in you ElasticSearch cluster with `curl -s http://localhost:9200/_cat/indices | cut -d ' '  -f3` ) or all indices with `_all` value.

## 3. Restore data
Restore will do the reverse actions : it reads backup in your snapshot directory and load indices in ElasticSearch cluster. This operation is done with this command :
```
$ curl -XPOST http://localhost:9200/_snapshot/the_hive_backup/snapshot_1/_restore
{
  "indices": "the_hive_8"
}
```