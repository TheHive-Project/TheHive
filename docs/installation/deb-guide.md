# Installation of TheHive using DEB package

Debian packages are published on Bintray repository. All packages are signed using the key 562CBC1C (fingerprint: 0CD5
AC59 DE5C 5A8E 0EE1  3849 3D99 BB18 562C BC1C):

```
echo 'deb https://dl.bintray.com/cert-bdf/debian any main' | sudo tee -a /etc/apt/sources.list.d/thehive-project.list
sudo apt-key adv --keyserver hkp://pgp.mit.edu --recv-key 562CBC1C
sudo apt-get update
sudo apt-get install thehive
```

After package installation, you should install ElasticSearch
(see [ElasticSearch installation guide](elasticsearch-guide.md)) and configure TheHive
(see [configuration guide](../admin/configuration.md))