# Change Log

## [Unreleased](https://github.com/TheHive-Project/TheHive/tree/3.4.0-RC1)(2019-06-05)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.4.0-RC1...3.4.0-RC2)

**Implemented enhancements:**

- Display ioc and sighted attributes in Alert artifact list [\#1035](https://github.com/TheHive-Project/TheHive/issues/1035)
- Merge Observable tags with existing observables during importing alerts into case [\#1014](https://github.com/TheHive-Project/TheHive/issues/1014)
- API not recognizing the attribute 'sighted' of artifacts on alert creation [\#1003](https://github.com/TheHive-Project/TheHive/issues/1003)
- Alerts are not getting deleted as expected [\#974](https://github.com/TheHive-Project/TheHive/issues/974)

**Fixed bugs:**

- Update case owner field validation to handle null value [\#1036](https://github.com/TheHive-Project/TheHive/issues/1036)
- thehive prints error messages on first run \("Authentication failure" / "user init not found"\) [\#1027](https://github.com/TheHive-Project/TheHive/issues/1027)
- TLP:WHITE for observable not shown, not editable [\#1025](https://github.com/TheHive-Project/TheHive/issues/1025)
- Dashboard based on observables not refreshing correctly [\#996](https://github.com/TheHive-Project/TheHive/issues/996)
- javascript error in tasks [\#979](https://github.com/TheHive-Project/TheHive/issues/979)
- /api/alert/{}/createCase does not use caseTemplate [\#929](https://github.com/TheHive-Project/TheHive/issues/929)

**Closed issues:**

- Cannot add custom fields to case template [\#1042](https://github.com/TheHive-Project/TheHive/issues/1042)
- sample hive does not connect to cortex and prints no helpful error message [\#1028](https://github.com/TheHive-Project/TheHive/issues/1028)

## [3.4.0-RC1](https://github.com/TheHive-Project/TheHive/tree/3.4.0-RC1) (2019-06-05)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.3.1...3.4.0-RC1)

**Implemented enhancements:**

- Allow to import file from Cortex report [\#982](https://github.com/TheHive-Project/TheHive/issues/982)
- Remove metrics module [\#975](https://github.com/TheHive-Project/TheHive/issues/975)
- Upgrade frontend libraries [\#966](https://github.com/TheHive-Project/TheHive/issues/966)
- Cortex AddArtifactToCase AssignCase [\#922](https://github.com/TheHive-Project/TheHive/issues/922)
- Communication to ElasticSearch via HTTP API 9200 [\#913](https://github.com/TheHive-Project/TheHive/issues/913)
- Support Elasticsearch 6.x clusters [\#623](https://github.com/TheHive-Project/TheHive/issues/623)
- Add Cortex AssignCase [\#924](https://github.com/TheHive-Project/TheHive/pull/924) ([zpriddy](https://github.com/zpriddy))

**Fixed bugs:**

- Authentication Error when using Hive API \(Patch\) [\#951](https://github.com/TheHive-Project/TheHive/issues/951)
- Bulk merge of alerts does not merge the tags [\#994](https://github.com/TheHive-Project/TheHive/issues/994)
- Java 11 build crash [\#990](https://github.com/TheHive-Project/TheHive/issues/990)
- Failure to load datatypes [\#988](https://github.com/TheHive-Project/TheHive/issues/988)
- Fix search page base filter [\#983](https://github.com/TheHive-Project/TheHive/issues/983)
- Donut dashboard metric values are not transformed to searches [\#972](https://github.com/TheHive-Project/TheHive/issues/972)

**Closed issues:**

- bintray repo for deb packages not signed [\#976](https://github.com/TheHive-Project/TheHive/issues/976)
- Set alert to status "Ignored" via API does not work [\#955](https://github.com/TheHive-Project/TheHive/issues/955)

**Merged pull requests:**

- Scalligraph [\#980](https://github.com/TheHive-Project/TheHive/pull/980) ([BillOTei](https://github.com/BillOTei))

## [3.3.1](https://github.com/TheHive-Project/TheHive/tree/3.3.1) (2019-05-22)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.3.0...3.3.1)

**Implemented enhancements:**

- Reduce unnecessary audit-logs for no-changes [\#935](https://github.com/TheHive-Project/TheHive/issues/935)

**Fixed bugs:**

- Observable value wiped on changing type field during creation [\#507](https://github.com/TheHive-Project/TheHive/issues/507)
- THP-SEC-ADV-2017-001: Privilege Escalation in all Versions of TheHive [\#408](https://github.com/TheHive-Project/TheHive/issues/408)

**Closed issues:**

- MISP-Warninglists analyzer reported as outdated but is up to date [\#970](https://github.com/TheHive-Project/TheHive/issues/970)
- Cannot attach file to task - 413 request entity too large [\#967](https://github.com/TheHive-Project/TheHive/issues/967)
- MISP export failing with invalid event reason  [\#963](https://github.com/TheHive-Project/TheHive/issues/963)
- Cortex "Error: user init not found" on first run [\#961](https://github.com/TheHive-Project/TheHive/issues/961)
- Problem send event from MISP to The Hive [\#950](https://github.com/TheHive-Project/TheHive/issues/950)
- 504 Gateway Time-out [\#941](https://github.com/TheHive-Project/TheHive/issues/941)
- Hive to Cortex API Authentication Failure [\#940](https://github.com/TheHive-Project/TheHive/issues/940)
- Different analyzer results between manually built instance and trainingVM. [\#934](https://github.com/TheHive-Project/TheHive/issues/934)



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*
