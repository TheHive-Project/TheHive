# Migration guide

## From 2.10.x to 2.11.x

### Database migration

At the first connection to TheHive 2.11, a migration of the database will be asked. This will create a new ElastciSearch
  index (the_hive_9). See [Updating](admin/updating.md).

### MISP to alert

MISP synchronization is now done using alerting framework. MISP events are seen like other alert. You can use
[TheHive4py](https://github.com/CERT-BDF/TheHive4py) to create your own alert.

### Configuration changes

#### MISP certificate authority deprecated

Specifying certificate authority in MISP configuration using "cert" key is now deprecated. You must replace it by 
- before:
```
misp {
  [...]
  cert = "/path/to/truststore.jks"
}
```
- after:
```
misp {
  [...]
  ws.ssl.trustManager.stores = [
    {
      type: "JKS"
      path: "/path/to/truststore.jks"
    }
  ]
}
```

`ws` key can be placed in MISP server section or in global MISP section. In the latter, ws configuration will be applied
  on all MISP instances.

#### Cortex and MISP HTTP client options

HTTP client used by Cortex and MISP is more configurable. Proxy can be configured, with or without authentication. Refer
 to [configuration](admin/configuration.md#8-http-client-configuration) for all possible options.


### Packages

RPM and DEB package is now provided. This make installation easier the using binary package (zip). See
[Debian installation guide](installation/deb-guide.md) and [RPM installation guid](installation/rpm-guide.md).