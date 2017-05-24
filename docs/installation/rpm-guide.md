# Installing TheHive Using an RPM Package

TheHive's RPM packages are published on our Bintray repository. All packages are PGP signed using the key which ID is [562CBC1C](/PGP-PUBLIC-KEY). The key's fingerprint is:

```0CD5 AC59 DE5C 5A8E 0EE1  3849 3D99 BB18 562C BC1C```

To intall TheHive from an RPM package, you'll need to begin by installing the RPM release package using the following command:
```
yum install install https://dl.bintray.com/cert-bdf/rpm/thehive-project-release-1.0.0-3.noarch.rpm
```
This will install TheHive Project's repository in `/etc/yum.repos.d/thehive-rpm.repo` and the GPG public key `in
/etc/pki/rpm-gpg/GPG-TheHive-Project`.
 
Once done, you will able to install TheHive package using yum:
```
yum install thehive
```

One installed, you should [install ElasticSearch](elasticsearch-guide.md) and [configure TheHive](../admin/configuration.md).
