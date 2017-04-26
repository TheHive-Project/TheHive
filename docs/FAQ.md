# Cases and Tasks

- [I Can't Add a Template](https://github.com/CERT-BDF/TheHive/wiki/FAQ#i-cant-add-a-template)
- [Why My Freshly Added Template Doesn't Show Up?](https://github.com/CERT-BDF/TheHive/wiki/FAQ#why-my-freshly-added-template-doesnt-show-up)
- [Can I Use a Specific Template for Imported MISP Events?](https://github.com/CERT-BDF/TheHive/wiki/FAQ#can-i-use-a-specific-template-for-imported-misp-events)

## Templates
### I Can't Add a Template
You need to log in as an administrator to add a template.

### Why My Freshly Added Template Doesn't Show Up?
When you add a new template and hit the `+NEW` button, you don't see it because unlike other events that you can see in the Flow, it is not broadcasted to all the user sessions. So you need to refresh the page before clicking the `+NEW` button.

You don't need to log out then log in again.

### Can I Use a Specific Template for Imported MISP Events?
Definitely! You just need to add a `caseTemplate` parameter in the section corresponding to the MISP connector in your `conf/application.conf` file. This is described in the [Administrator's Guide](https://github.com/CERT-BDF/TheHive/wiki/Administrator's-guide#48-misp).

# Analyzers
- [I Would Like to Contribute or Request a New Analyzer](https://github.com/CERT-BDF/TheHive/wiki/FAQ#i-would-like-to-contribute-or-request-a-new-analyzer)

## General
### I Would Like to Contribute or Request a New Analyzer
Analyzers are no longer bundled with TheHive. Since the release of Buckfast (TheHive 2.10), the analysis engine has been released as a separate product called [Cortex](https://github.com/CERT-BDF/Cortex). If you'd like to develop or ask for an analyzer that will help you get the most out of TheHive, please open a [feature request](https://github.com/CERT-BDF/Cortex-Analyzers/issues/new) first. This will give us a chance to validate the use cases and avoid having multiple persons working on the same analyzer.

Once validated, you can either develop your analyzer or wait for THeHive Project or a contributor to undertake the task and if everything is alright, we will schedule its addition to a future Cortex release.

# Miscellaneous Questions

- [Can I Enable HTTPS to Connect to TheHive?](https://github.com/CERT-BDF/TheHive/wiki/FAQ#can-i-enable-https-to-connect-to-thehive)
- [Can I Import Events from Multiple MISP Servers?](https://github.com/CERT-BDF/TheHive/wiki/FAQ#can-i-import-events-from-multiple-misp-servers)
- [Can I connect TheHive to a AWS ElasticSearch service ?](https://github.com/CERT-BDF/TheHive/wiki/FAQ#can-i-connect-thehive-to-an-aws-elasticsearch-service)
- [Any plan to support elasticsearch 5.x backend in the future ?](https://github.com/CERT-BDF/TheHive/wiki/FAQ#do-you-have-any-plans-for-elasticsearch-5x-support-in-the-future)

### Can I Enable HTTPS to Connect to TheHive?
#### TL;DR
Add the following lines to `/etc/thehive/application.conf`

    https.port: 9443
    play.server.https.keyStore {
      path: "/path/to/keystore.jks"
      type: "JKS"
      password: "password_of_keystore"
    }

HTTP can disabled by adding line `http.port=disabled`
#### Details
Please read the [relevant section](https://github.com/CERT-BDF/TheHive/wiki/Configuration#9-https) in the Configuration guide.

### Can I Import Events from Multiple MISP Servers?
Yes, this is possible. For each MISP server, add a `misp` section in your `conf/application.conf` file as described in the [Administrator's Guide](htthttps://github.com/CERT-BDF/TheHive/wiki/Configuration#7-misp).

### Can I connect TheHive to an AWS ElasticSearch service?
AWS Elasticsearch service only supports HTTP transport protocol. It does not support the binary protocol which the Java client used by TheHive relies on to communicate with ElasticSearch. As a result, it is not possible to setup TheHive with AWS Elasticsearch service. More information is available at the following URLs:
- [http://docs.aws.amazon.com/elasticsearch-service/latest/developerguide/aes-limits.html](https://www.elastic.co/guide/en/elasticsearch/reference/5.1/modules-network.html#_transport_and_http_protocols )

> “TCP Transport	: The service supports HTTP on port 80, but does not support TCP transport”

- [https://www.elastic.co/guide/en/elasticsearch/reference/5.1/modules-network.html#_transport_and_http_protocols](https://www.elastic.co/guide/en/elasticsearch/reference/5.1/modules-network.html#_transport_and_http_protocols)
> “TCP Transport : Used for communication between nodes in the cluster, by the Java Transport client and by the Tribe node.
> HTTP: Exposes the JSON-over-HTTP interface used by all clients other than the Java clients.”

### Do you have any plans for ElasticSearch 5.x support in the future?
We haven't planned it yet. Please note that it's easier to move from ES2 to ES5 than from 1.x to version 2.
We will give it a try as soon as we can and let you know.
