# Change Log

## [3.5.2](https://github.com/TheHive-Project/TheHive/milestone/92) (2022-06-22)

**Fixed bugs:**

- Update libraries [\#2395](https://github.com/TheHive-Project/TheHive/issues/2395)

## [3.5.1](https://github.com/TheHive-Project/TheHive/milestone/65) (2021-03-01)

**Fixed bugs:**

- [Bug] Init Script Syntax Error in Bash Switch-Case [\#1646](https://github.com/TheHive-Project/TheHive/issues/1646)
- [Bug] Issues with case attachments section [\#1651](https://github.com/TheHive-Project/TheHive/issues/1651)
- [Bug] Fix the severity component [\#1654](https://github.com/TheHive-Project/TheHive/issues/1654)
- [Bug] Display problem TH [\#1688](https://github.com/TheHive-Project/TheHive/issues/1688)
- [Bug] Update doesn't work on Elasticsearch 7.11 [\#1799](https://github.com/TheHive-Project/TheHive/issues/1799)

**Pull requests:**

- Add misp thread pool to sample application conf [\#1632](https://github.com/TheHive-Project/TheHive/pull/1632)
- #1646 make check_requirements work properly [\#1667](https://github.com/TheHive-Project/TheHive/pull/1667)

## [3.4.4](https://github.com/TheHive-Project/TheHive/milestone/63) (2020-10-30)

**Fixed bugs:**

- [Security] Update Playframework [\#1604](https://github.com/TheHive-Project/TheHive/issues/1604)

## [3.5.0](https://github.com/TheHive-Project/TheHive/milestone/61) (2020-10-29)

**Implemented enhancements:**

- Not possible to import new alerts to cases when drilling down from dashboard [\#1218](https://github.com/TheHive-Project/TheHive/issues/1218)
- Bug: Responder-list is unordered [\#1564](https://github.com/TheHive-Project/TheHive/issues/1564)

**Fixed bugs:**

- [Bug] Can't tag observable as IOC in Alert [\#1335](https://github.com/TheHive-Project/TheHive/issues/1335)
- [Bug] Concurrent access fails [\#1570](https://github.com/TheHive-Project/TheHive/issues/1570)
- [Bug] Pivoting from dashboard to search page is loosing the date filter [\#1581](https://github.com/TheHive-Project/TheHive/issues/1581)
- [Bug] Report template admin page [\#1591](https://github.com/TheHive-Project/TheHive/issues/1591)
- [Security] Update Playframework [\#1603](https://github.com/TheHive-Project/TheHive/issues/1603)

**Pull requests:**

- Add tag filtering in Admin UI settings [\#1171](https://github.com/TheHive-Project/TheHive/pull/1171)
- Fix SSL configuration sample [\#1559](https://github.com/TheHive-Project/TheHive/pull/1559)

**Closed issues:**

- [Bug] Click on short report resolves outdated long report [\#1350](https://github.com/TheHive-Project/TheHive/issues/1350)

## [3.4.3](https://github.com/TheHive-Project/TheHive/milestone/62) (2020-10-26)

**Fixed bugs:**

- [Bug] TheHive is stalled while importing Alerts with a large number of observables [\#1416](https://github.com/TheHive-Project/TheHive/issues/1416)

## [3.5.0-RC1](https://github.com/TheHive-Project/TheHive/milestone/44) (2020-08-12)

**Implemented enhancements:**

- Support of ElasticSearch 7 [\#1377](https://github.com/TheHive-Project/TheHive/issues/1377)
- [Enhancement] MISP sync [\#1398](https://github.com/TheHive-Project/TheHive/issues/1398)

**Fixed bugs:**

- [Bug] OAuth2/OpenIDC Authentication failure [\#1291](https://github.com/TheHive-Project/TheHive/issues/1291)
- [Feature Request] OAuth support for Basic authentication to authorization server's tokenUrl [\#1294](https://github.com/TheHive-Project/TheHive/issues/1294)
- [Bug] Can't auth with SSO/OAuth with FusionAuth [\#1342](https://github.com/TheHive-Project/TheHive/issues/1342)

**Pull requests:**

- Add link to add a task to a case template [\#1049](https://github.com/TheHive-Project/TheHive/pull/1049)

**Closed issues:**

- OAuth2 not working : Authentication failure [\#946](https://github.com/TheHive-Project/TheHive/issues/946)

## [3.4.2](https://github.com/TheHive-Project/TheHive/milestone/57) (2020-04-25)

**Implemented enhancements:**

- [Feature Request] Providing output details for Responders [\#962](https://github.com/TheHive-Project/TheHive/issues/962)

**Fixed bugs:**

- Analyzer's artifacts tags and message are not kept when importing observables [\#1285](https://github.com/TheHive-Project/TheHive/issues/1285)
- [Bug] File observables in alert are not created in case [\#1292](https://github.com/TheHive-Project/TheHive/issues/1292)

## [3.4.1](https://github.com/TheHive-Project/TheHive/milestone/53) (2020-04-17)

**Implemented enhancements:**

- docker: TheHive fails to connect to elasticsearch (NoNodeAvailableException) [\#854](https://github.com/TheHive-Project/TheHive/issues/854)
- Improved support for OpenID connect and OAuth2 [\#1110](https://github.com/TheHive-Project/TheHive/issues/1110)
- TheHive's Docker entrypoint logs the Play secret key at startup [\#1177](https://github.com/TheHive-Project/TheHive/issues/1177)
- [Q] Configure TheHive's first run using Docker Compose [\#1199](https://github.com/TheHive-Project/TheHive/issues/1199)
- TheHive's docker containers should be orchestration-ready [\#1204](https://github.com/TheHive-Project/TheHive/issues/1204)
- MISP synchronisation: map to_ids to ioc [\#1273](https://github.com/TheHive-Project/TheHive/issues/1273)

**Fixed bugs:**

- MISP & TheHive out of sync? [\#866](https://github.com/TheHive-Project/TheHive/issues/866)
- Owner is case-sensitive on api calls [\#928](https://github.com/TheHive-Project/TheHive/issues/928)
- Bug: Observable without data breaks display of observables [\#1080](https://github.com/TheHive-Project/TheHive/issues/1080)
- Docker-Compose ElasticSearch incompatibility [\#1140](https://github.com/TheHive-Project/TheHive/issues/1140)
- [Bug] Analyzers that take more than 10 Minutes run into timeout [\#1156](https://github.com/TheHive-Project/TheHive/issues/1156)
- TheHive 3.4.0 migration logs errors ([error] m.Migration - Failed to create dashboard) [\#1202](https://github.com/TheHive-Project/TheHive/issues/1202)
- Computed metrics is not compatible with painless scripting language [\#1210](https://github.com/TheHive-Project/TheHive/issues/1210)
- OAuth2 Bearer header should be of the format "Authorization Bearer" ? [\#1228](https://github.com/TheHive-Project/TheHive/issues/1228)
- Health API endpoint returns warning when everything is OK [\#1233](https://github.com/TheHive-Project/TheHive/issues/1233)
- [Bug] Job submission sometimes fails when multiple Cortex servers  [\#1272](https://github.com/TheHive-Project/TheHive/issues/1272)

**Closed issues:**

- Include Dockerfile in root of project [\#1222](https://github.com/TheHive-Project/TheHive/issues/1222)
- Docker user daemon with id 1 causes permission issues with local [\#1227](https://github.com/TheHive-Project/TheHive/issues/1227)

## [3.4.0](https://github.com/TheHive-Project/TheHive/milestone/52) (2019-09-09)

**Implemented enhancements:**

- Removing custom fields [\#954](https://github.com/TheHive-Project/TheHive/issues/954)

**Fixed bugs:**

- Can't secure ElasticSearch connection [\#1046](https://github.com/TheHive-Project/TheHive/issues/1046)
- Case statistics dashboard loads with an error message and the case over time panel fails to display any data [\#1050](https://github.com/TheHive-Project/TheHive/issues/1050)
- Cannot setup TheHive 3.4.0-RC2 using Docker [\#1051](https://github.com/TheHive-Project/TheHive/issues/1051)
- Incorrect tag filter results when observables with tags are added then deleted [\#1061](https://github.com/TheHive-Project/TheHive/issues/1061)
- Incorrect number of related observables returned [\#1062](https://github.com/TheHive-Project/TheHive/issues/1062)
- bulk merge alerts into case lose description's alert [\#1065](https://github.com/TheHive-Project/TheHive/issues/1065)
- Update Database button does not appear in training appliance [\#1067](https://github.com/TheHive-Project/TheHive/issues/1067)
- Cosmetic Bug: wrong number of exported observables displayed [\#1071](https://github.com/TheHive-Project/TheHive/issues/1071)
- 3.4 RC2 doesn't prompt to update/create the database when one doesn't exist [\#1107](https://github.com/TheHive-Project/TheHive/issues/1107)

## [3.4.0-RC2](https://github.com/TheHive-Project/TheHive/milestone/51) (2019-07-11)

**Implemented enhancements:**

- Alerts are not getting deleted as expected [\#974](https://github.com/TheHive-Project/TheHive/issues/974)
- API not recognizing the attribute 'sighted' of artifacts on alert creation [\#1003](https://github.com/TheHive-Project/TheHive/issues/1003)
- Merge Observable tags with existing observables during importing alerts into case [\#1014](https://github.com/TheHive-Project/TheHive/issues/1014)
- Display ioc and sighted attributes in Alert artifact list [\#1035](https://github.com/TheHive-Project/TheHive/issues/1035)

**Fixed bugs:**

- /api/alert/{}/createCase does not use caseTemplate [\#929](https://github.com/TheHive-Project/TheHive/issues/929)
- javascript error in tasks [\#979](https://github.com/TheHive-Project/TheHive/issues/979)
- Dashboard based on observables not refreshing correctly [\#996](https://github.com/TheHive-Project/TheHive/issues/996)
- TLP:WHITE for observable not shown, not editable [\#1025](https://github.com/TheHive-Project/TheHive/issues/1025)
- thehive prints error messages on first run ("Authentication failure" / "user init not found") [\#1027](https://github.com/TheHive-Project/TheHive/issues/1027)
- Update case owner field validation to handle null value [\#1036](https://github.com/TheHive-Project/TheHive/issues/1036)

**Closed issues:**

- can't add custom fields to case in 3.4.0-RC1 [\#1026](https://github.com/TheHive-Project/TheHive/issues/1026)
- sample hive does not connect to cortex and prints no helpful error message [\#1028](https://github.com/TheHive-Project/TheHive/issues/1028)

## [3.4.0-RC1](https://github.com/TheHive-Project/TheHive/milestone/49) (2019-06-05)

**Implemented enhancements:**

- Support Elasticsearch 6.x clusters [\#623](https://github.com/TheHive-Project/TheHive/issues/623)
- Communication to ElasticSearch via HTTP API 9200 [\#913](https://github.com/TheHive-Project/TheHive/issues/913)
- Cortex AddArtifactToCase AssignCase [\#922](https://github.com/TheHive-Project/TheHive/issues/922)
- AddArtifactToCase for cortex [\#923](https://github.com/TheHive-Project/TheHive/pull/923)
- Add Cortex AssignCase [\#924](https://github.com/TheHive-Project/TheHive/pull/924)
- Add 'My open cases' and 'New & Updated alerts' to quick filters [\#925](https://github.com/TheHive-Project/TheHive/pull/925)
- Upgrade frontend libraries [\#966](https://github.com/TheHive-Project/TheHive/issues/966)
- Remove metrics module [\#975](https://github.com/TheHive-Project/TheHive/issues/975)
- Allow to import file from Cortex report [\#982](https://github.com/TheHive-Project/TheHive/issues/982)

**Fixed bugs:**

- Donut dashboard metric values are not transformed to searches [\#972](https://github.com/TheHive-Project/TheHive/issues/972)
- Fix search page base filter [\#983](https://github.com/TheHive-Project/TheHive/issues/983)
- Failure to load datatypes [\#988](https://github.com/TheHive-Project/TheHive/issues/988)
- Java 11 build crash [\#990](https://github.com/TheHive-Project/TheHive/issues/990)
- Bulk merge of alerts does not merge the tags [\#994](https://github.com/TheHive-Project/TheHive/issues/994)

**Closed issues:**

- Have AlertFilter for "New&Updated" [\#952](https://github.com/TheHive-Project/TheHive/issues/952)

## [3.3.1](https://github.com/TheHive-Project/TheHive/milestone/50) (2019-05-22)

**Fixed bugs:**

- THP-SEC-ADV-2017-001: Privilege Escalation in all Versions of TheHive [\#408](https://github.com/TheHive-Project/TheHive/issues/408)

## [3.3.0](https://github.com/TheHive-Project/TheHive/milestone/48) (2019-03-19)

**Fixed bugs:**

- Merge case by CaseID Broken [\#930](https://github.com/TheHive-Project/TheHive/issues/930)

## [3.3.0-RC6](https://github.com/TheHive-Project/TheHive/milestone/47) (2019-03-19)

**Implemented enhancements:**

- Support for filtering Tags by prefix (using asterisk, % or something) in search dialog [\#666](https://github.com/TheHive-Project/TheHive/issues/666)
- Empty case still available when disabled [\#901](https://github.com/TheHive-Project/TheHive/issues/901)
- Dashboards - Add text widget [\#908](https://github.com/TheHive-Project/TheHive/issues/908)
- Add Tags to an Alert with Responder [\#912](https://github.com/TheHive-Project/TheHive/issues/912)

## [3.3.0-RC5](https://github.com/TheHive-Project/TheHive/milestone/46) (2019-02-23)

**Implemented enhancements:**

- Mouseover text for alert preview [\#897](https://github.com/TheHive-Project/TheHive/issues/897)

**Fixed bugs:**

- Search results not visible [\#895](https://github.com/TheHive-Project/TheHive/issues/895)
- dashboard clicks are not correctly translated to tag filters [\#896](https://github.com/TheHive-Project/TheHive/issues/896)

## [3.3.0-RC4](https://github.com/TheHive-Project/TheHive/milestone/45) (2019-02-22)

**Implemented enhancements:**

- Use empty case modal when merging alerts and no templates are defined [\#893](https://github.com/TheHive-Project/TheHive/issues/893)

**Fixed bugs:**

- Hide Empty Case Button Broken [\#890](https://github.com/TheHive-Project/TheHive/issues/890)
- Issue with navigation from dashboard clickable donuts to search page [\#894](https://github.com/TheHive-Project/TheHive/issues/894)

## [3.3.0 RC3](https://github.com/TheHive-Project/TheHive/milestone/43) (2019-02-21)

**Implemented enhancements:**

- Ability to disable "New Case" -> "Empty case" [\#449](https://github.com/TheHive-Project/TheHive/issues/449)
- Disable clickable widgets in dashboard edit mode [\#485](https://github.com/TheHive-Project/TheHive/issues/485)
- Feature Request: link to and from Hive to MISP [\#820](https://github.com/TheHive-Project/TheHive/issues/820)
- Improvement: Upload of observables seem to fail "silently" [\#829](https://github.com/TheHive-Project/TheHive/issues/829)
- Feature: Add "auto-completion" to the UI [\#831](https://github.com/TheHive-Project/TheHive/issues/831)
- Related artifacts: IOC/not IOC [\#838](https://github.com/TheHive-Project/TheHive/issues/838)
- [BUG] Audit trail for alert ignore [\#863](https://github.com/TheHive-Project/TheHive/issues/863)
- Provide a quick link to copy alert id [\#870](https://github.com/TheHive-Project/TheHive/issues/870)
- Update Copyright with year 2019 [\#879](https://github.com/TheHive-Project/TheHive/issues/879)
- Add a Related Alerts link to case details view [\#884](https://github.com/TheHive-Project/TheHive/issues/884)
- Add a UI configuration admin section [\#888](https://github.com/TheHive-Project/TheHive/issues/888)

**Fixed bugs:**

- Alert updates and tracking (follow) [\#856](https://github.com/TheHive-Project/TheHive/issues/856)
- Cortex responders with DataType `thehive:case_artifact` do not show up within thehive when attempting to run them for observables. [\#869](https://github.com/TheHive-Project/TheHive/issues/869)
- Log message related to MISP synchronization is confusing [\#871](https://github.com/TheHive-Project/TheHive/issues/871)
- Label Typo in Updated Alerts [\#874](https://github.com/TheHive-Project/TheHive/issues/874)
- AKKA version missmatch [\#877](https://github.com/TheHive-Project/TheHive/issues/877)
- Drone build fails on pull-requests [\#882](https://github.com/TheHive-Project/TheHive/issues/882)

## [3.3.0 RC2](https://github.com/TheHive-Project/TheHive/milestone/42) (2019-02-12)

**Fixed bugs:**

- Java dependency of DEB package is broken [\#867](https://github.com/TheHive-Project/TheHive/issues/867)

## [3.3.0 RC1](https://github.com/TheHive-Project/TheHive/milestone/41) (2019-02-06)

**Implemented enhancements:**

- Bulk Merge Alerts into Case [\#271](https://github.com/TheHive-Project/TheHive/issues/271)
- Tag normalization [\#657](https://github.com/TheHive-Project/TheHive/pull/657)
- Improve case template selection for case creation [\#769](https://github.com/TheHive-Project/TheHive/issues/769)
- sorting in alerts [\#824](https://github.com/TheHive-Project/TheHive/issues/824)
- Merge alerts directly to a case [\#826](https://github.com/TheHive-Project/TheHive/issues/826)
- MISP - Add an Event Tag instead of/additionnally to Attribute Tag [\#836](https://github.com/TheHive-Project/TheHive/issues/836)
- Add support to Java versions, higher than 8 [\#861](https://github.com/TheHive-Project/TheHive/issues/861)
- [BUG] Session cookie received with API token  [\#864](https://github.com/TheHive-Project/TheHive/issues/864)

**Fixed bugs:**

- Delete user from Thehive:   DELETE /api/user/user1 returned 500 org.elastic4play.InternalError: user can't be removed [\#844](https://github.com/TheHive-Project/TheHive/issues/844)
- Assigned Tasks do not show up in 'My Tasks' before they are started [\#845](https://github.com/TheHive-Project/TheHive/issues/845)

## [3.2.1](https://github.com/TheHive-Project/TheHive/milestone/40) (2019-01-02)

**Fixed bugs:**

- Tag order is reversed if a case is created from an alert [\#810](https://github.com/TheHive-Project/TheHive/issues/810)
- Potential Regression: Case templates cannot be exported in 3.2.0 [\#823](https://github.com/TheHive-Project/TheHive/issues/823)
- Can't unset case template when alert is imported [\#825](https://github.com/TheHive-Project/TheHive/issues/825)
- Bug UI "Tooltip" / Hint is cropped by window borders [\#832](https://github.com/TheHive-Project/TheHive/issues/832)

## [3.2.0](https://github.com/TheHive-Project/TheHive/milestone/39) (2018-12-11)

**Implemented enhancements:**

- Add configuration for drone continuous integration [\#803](https://github.com/TheHive-Project/TheHive/issues/803)

**Fixed bugs:**

- Error when uploading password protected zips as observables [\#805](https://github.com/TheHive-Project/TheHive/issues/805)
- Lowercase user ID coming from HTTP header [\#808](https://github.com/TheHive-Project/TheHive/issues/808)

## [3.2.0-RC1](https://github.com/TheHive-Project/TheHive/milestone/16) (2018-11-21)

**Implemented enhancements:**

- Whitelist of tags for MISP alerts [\#481](https://github.com/TheHive-Project/TheHive/issues/481)
- Support header variable authentication [\#554](https://github.com/TheHive-Project/TheHive/issues/554)
- Add confirmation dialogs when running a responder [\#762](https://github.com/TheHive-Project/TheHive/issues/762)
- Observable Value gets cleared when changing its type (importing it from an analyser result) [\#763](https://github.com/TheHive-Project/TheHive/issues/763)
- Show tags of observables in Alert preview [\#778](https://github.com/TheHive-Project/TheHive/issues/778)
- Update Play [\#791](https://github.com/TheHive-Project/TheHive/issues/791)
- Show observable description on mouseover observables [\#793](https://github.com/TheHive-Project/TheHive/issues/793)
- Add responder actions in dashboard [\#794](https://github.com/TheHive-Project/TheHive/issues/794)
- Add ability to add a log in responder operation [\#795](https://github.com/TheHive-Project/TheHive/issues/795)

**Fixed bugs:**

- Intermittently losing Cortex [\#739](https://github.com/TheHive-Project/TheHive/issues/739)
- Case search from dashboard clic "invalid filters error" [\#761](https://github.com/TheHive-Project/TheHive/issues/761)
- Basic authentication method should be disabled by default [\#772](https://github.com/TheHive-Project/TheHive/issues/772)
- A user with "write" permission can delete a case using API [\#773](https://github.com/TheHive-Project/TheHive/issues/773)
- Observable creation doesn't allow multiline observables [\#790](https://github.com/TheHive-Project/TheHive/issues/790)
- MISP synchronization fails if event contains attachment with invalid name [\#801](https://github.com/TheHive-Project/TheHive/issues/801)

**Pull requests:**

- Added Integration with FireEye iSIGHT [\#755](https://github.com/TheHive-Project/TheHive/pull/755)

## [3.1.2](https://github.com/TheHive-Project/TheHive/milestone/38) (2018-10-12)

**Fixed bugs:**

- Cortex polling settings break startup [\#754](https://github.com/TheHive-Project/TheHive/issues/754)

## [3.1.1](https://github.com/TheHive-Project/TheHive/milestone/37) (2018-10-12)

**Implemented enhancements:**

- url category to MISP: poll for default [\#732](https://github.com/TheHive-Project/TheHive/issues/732)
- Publish stable versions in beta package channels [\#733](https://github.com/TheHive-Project/TheHive/issues/733)
- Change Debian dependencies [\#751](https://github.com/TheHive-Project/TheHive/issues/751)
- Allow TheHive to use a custom root context [\#752](https://github.com/TheHive-Project/TheHive/issues/752)

**Fixed bugs:**

- UPN attribute is not correctly lowercased [\#736](https://github.com/TheHive-Project/TheHive/issues/736)
- Observable Result Icons Not Displaying [\#738](https://github.com/TheHive-Project/TheHive/issues/738)
- Update breaks RHEL [\#743](https://github.com/TheHive-Project/TheHive/issues/743)
- Console output should not be logged in syslog [\#749](https://github.com/TheHive-Project/TheHive/issues/749)

## [3.1.0](https://github.com/TheHive-Project/TheHive/milestone/36) (2018-09-25)

**Implemented enhancements:**

- 3.1.0RC3: Browsing to negative case ids is possible [\#713](https://github.com/TheHive-Project/TheHive/issues/713)
- AddCustomField responder operation [\#724](https://github.com/TheHive-Project/TheHive/issues/724)
- Add MarkAlertAsRead action to responders [\#729](https://github.com/TheHive-Project/TheHive/issues/729)

**Fixed bugs:**

- Fix PAP labels [\#711](https://github.com/TheHive-Project/TheHive/issues/711)
- 3.0.1RC3: certificate based authentication failes as attributes are not correctly lowercased [\#714](https://github.com/TheHive-Project/TheHive/issues/714)
- API allows alert creation with duplicate artifacts [\#720](https://github.com/TheHive-Project/TheHive/issues/720)
- Multiple responder actions does not seem to be handled [\#722](https://github.com/TheHive-Project/TheHive/issues/722)
- TheHive Hyperlinking  [\#723](https://github.com/TheHive-Project/TheHive/issues/723)

**Pull requests:**

- Add AddTagToArtifact action to responders [\#717](https://github.com/TheHive-Project/TheHive/pull/717)

**Closed issues:**

- TheHive:Alerts don't send observables to Responders [\#725](https://github.com/TheHive-Project/TheHive/issues/725)

## [3.1.0-RC3](https://github.com/TheHive-Project/TheHive/milestone/35) (2018-09-06)

**Implemented enhancements:**

- Filter on computedHandlingDuration in SearchDialog fails [\#688](https://github.com/TheHive-Project/TheHive/issues/688)
- Search section: Search for a string over all types of objects [\#689](https://github.com/TheHive-Project/TheHive/issues/689)
- Related Cases: See (x) more links [\#690](https://github.com/TheHive-Project/TheHive/issues/690)
- Make task group input optional [\#696](https://github.com/TheHive-Project/TheHive/issues/696)
- Display task group in global task lists [\#705](https://github.com/TheHive-Project/TheHive/issues/705)
- Change layout of observable creation form [\#706](https://github.com/TheHive-Project/TheHive/pull/706)
- Allow task group auto complete in case template admin section [\#707](https://github.com/TheHive-Project/TheHive/issues/707)
- Display task description via a collapsible row [\#709](https://github.com/TheHive-Project/TheHive/issues/709)

**Fixed bugs:**

- Start waiting tasks when adding task logs [\#695](https://github.com/TheHive-Project/TheHive/issues/695)
- Error handling deletion and re creation of file observables [\#699](https://github.com/TheHive-Project/TheHive/issues/699)
- PKI authentication fails if user name in certificate has the wrong case [\#700](https://github.com/TheHive-Project/TheHive/issues/700)
- .sbt build of current git version fails with x-pack-transport error [\#710](https://github.com/TheHive-Project/TheHive/issues/710)

## [3.1.0-RC2](https://github.com/TheHive-Project/TheHive/milestone/34) (2018-08-30)

**Implemented enhancements:**

- Observable type boxes doesn't line break on alert preview pane [\#593](https://github.com/TheHive-Project/TheHive/issues/593)
- Application.conf needs clarifications [\#606](https://github.com/TheHive-Project/TheHive/issues/606)
- Ability to set custom fields as mandatory [\#652](https://github.com/TheHive-Project/TheHive/issues/652)
-  On branch betterDescriptions [\#660](https://github.com/TheHive-Project/TheHive/pull/660)
- TheHive 3.1RC1: Add Username that executes an active response to json data field of responder [\#662](https://github.com/TheHive-Project/TheHive/issues/662)
- TheHive 3.1RC1: Add status to cases and tasks in new search page [\#663](https://github.com/TheHive-Project/TheHive/issues/663)
- TheHive 3.1RC1: Slow reaction if Cortex is (unclear) unreachable [\#664](https://github.com/TheHive-Project/TheHive/issues/664)
- x509 certificate authentication option 'wantClientAuth' [\#667](https://github.com/TheHive-Project/TheHive/issues/667)
- Remember task list configuration (grouped/list) [\#681](https://github.com/TheHive-Project/TheHive/issues/681)
- MISP Exports in livestream miss hyperlink to caseid [\#684](https://github.com/TheHive-Project/TheHive/issues/684)
- Add a search box to quickly search for case by caseId [\#685](https://github.com/TheHive-Project/TheHive/issues/685)

**Fixed bugs:**

- Dashboard visualizations do not work with custom fields [\#478](https://github.com/TheHive-Project/TheHive/issues/478)
- Horizontal Scrolling and Word-Wrap options for Logs [\#573](https://github.com/TheHive-Project/TheHive/issues/573)
- 'Tagged as' displayed in Related Cases even if cases are untagged [\#594](https://github.com/TheHive-Project/TheHive/issues/594)
- play.crypto.secret is depecrated [\#671](https://github.com/TheHive-Project/TheHive/issues/671)
- WebUI inaccessible after upgrading to 3.1.0-0-RC1 (elastic4play and Play exceptions) [\#674](https://github.com/TheHive-Project/TheHive/issues/674)
- 3.1.0-RC1- Tasks list is limited to 10 items. [\#679](https://github.com/TheHive-Project/TheHive/issues/679)

**Pull requests:**

- Move input group addons from right to left for better usage [\#672](https://github.com/TheHive-Project/TheHive/pull/672)

## [3.1.0-RC1 (Cerana 1)](https://github.com/TheHive-Project/TheHive/milestone/7) (2018-08-20)

**Implemented enhancements:**

- Ability to have nested tasks [\#148](https://github.com/TheHive-Project/TheHive/issues/148)
- Output of analyzer as new observable [\#246](https://github.com/TheHive-Project/TheHive/issues/246)
- Single-Sign On support [\#354](https://github.com/TheHive-Project/TheHive/issues/354)
- MISP Sharing Improvements [\#366](https://github.com/TheHive-Project/TheHive/issues/366)
- Make The Hive MISP integration sharing vs pull configurable [\#374](https://github.com/TheHive-Project/TheHive/issues/374)
- StreamSrv: Unexpected message : StreamNotFound [\#414](https://github.com/TheHive-Project/TheHive/issues/414)
- Assign Tasks to users from the Tasks tab [\#426](https://github.com/TheHive-Project/TheHive/issues/426)
- OAuth2 single sign-on implementation (BE + FE) [\#430](https://github.com/TheHive-Project/TheHive/pull/430)
- Auto-refresh for Dashboards [\#476](https://github.com/TheHive-Project/TheHive/issues/476)
- Handling malware as zip protected file [\#538](https://github.com/TheHive-Project/TheHive/issues/538)
- Start Task  - Button [\#540](https://github.com/TheHive-Project/TheHive/issues/540)
- Consider providing checksums for the release files [\#590](https://github.com/TheHive-Project/TheHive/issues/590)
- Ability to execute active response on any element of TheHive [\#609](https://github.com/TheHive-Project/TheHive/issues/609)
- Add PAP to case to indicate which kind of action is allowed [\#616](https://github.com/TheHive-Project/TheHive/issues/616)
- New TheHive-Project repository [\#618](https://github.com/TheHive-Project/TheHive/issues/618)
- Revamp the search section capabilities [\#620](https://github.com/TheHive-Project/TheHive/issues/620)
- Check Cortex authentication in status page [\#625](https://github.com/TheHive-Project/TheHive/issues/625)
- Custom fields in Alerts? [\#635](https://github.com/TheHive-Project/TheHive/issues/635)
- Display drop-down for custom fields sorted alphabetically [\#653](https://github.com/TheHive-Project/TheHive/issues/653)

**Fixed bugs:**

- Previewing alerts fails with "too many substreams open" due to case similarity process [\#280](https://github.com/TheHive-Project/TheHive/issues/280)
- File upload when /tmp is full [\#321](https://github.com/TheHive-Project/TheHive/issues/321)
- If cortex modules fails in some way, it is permanently repolled by TheHive [\#324](https://github.com/TheHive-Project/TheHive/issues/324)
- Artifacts reports are not merged when merging cases [\#446](https://github.com/TheHive-Project/TheHive/issues/446)
- Error with Single Sign-On on TheHive with X.509 Certificates [\#600](https://github.com/TheHive-Project/TheHive/issues/600)
- Dashboards contain analyzer IDs instead of correct names [\#608](https://github.com/TheHive-Project/TheHive/issues/608)
- Session does not expire correctly [\#640](https://github.com/TheHive-Project/TheHive/issues/640)
- Attachments with character "#" in the filename are wrongly proceesed [\#645](https://github.com/TheHive-Project/TheHive/issues/645)
- Default value of custom fields are not saved [\#649](https://github.com/TheHive-Project/TheHive/issues/649)

**Pull requests:**

- Zip file upload [\#643](https://github.com/TheHive-Project/TheHive/pull/643)

**Closed issues:**

- Is X-Pack enabled TLS for elasticsearch supported? [\#611](https://github.com/TheHive-Project/TheHive/issues/611)
- add double quotes in mini reports [\#634](https://github.com/TheHive-Project/TheHive/issues/634)

## [3.0.10](https://github.com/TheHive-Project/TheHive/milestone/33) (2018-06-09)

**Implemented enhancements:**

- Time Calculation for individual tasks [\#546](https://github.com/TheHive-Project/TheHive/issues/546)
- Sort related cases by related artifacts amount [\#548](https://github.com/TheHive-Project/TheHive/issues/548)
- Poll for connectors status and display  [\#563](https://github.com/TheHive-Project/TheHive/issues/563)
- Send caseId to Cortex analyzer [\#564](https://github.com/TheHive-Project/TheHive/issues/564)
- Rotate logs [\#579](https://github.com/TheHive-Project/TheHive/issues/579)

**Fixed bugs:**

- Short Report is not shown on observables (3.0.8) [\#512](https://github.com/TheHive-Project/TheHive/issues/512)
- MISP Synchronisation error [\#522](https://github.com/TheHive-Project/TheHive/issues/522)
- Making dashboards private makes them "invisible" [\#555](https://github.com/TheHive-Project/TheHive/issues/555)
- Open cases not listed after deletion of merged case in UI [\#557](https://github.com/TheHive-Project/TheHive/issues/557)
- Merge case by ID brings red error message if not a number in textfield [\#583](https://github.com/TheHive-Project/TheHive/issues/583)
- Invalid searches lead to read error messages [\#584](https://github.com/TheHive-Project/TheHive/issues/584)
- Analyzer name not reflected in modal view of mini-reports [\#586](https://github.com/TheHive-Project/TheHive/issues/586)
- Wrong error message when creating a observable with invalid data [\#592](https://github.com/TheHive-Project/TheHive/issues/592)

## [3.0.9](https://github.com/TheHive-Project/TheHive/milestone/32) (2018-04-13)

**Fixed bugs:**

- TheHive MISP cert validation, the trustAnchors parameter must be non-empty [\#452](https://github.com/TheHive-Project/TheHive/issues/452)
- Artifacts' sighted flags are not merged when merging cases [\#518](https://github.com/TheHive-Project/TheHive/issues/518)
- Long Report isn't shown [\#527](https://github.com/TheHive-Project/TheHive/issues/527)
- Error when trying to analyze a filename with the Hybrid Analysis analyzer [\#530](https://github.com/TheHive-Project/TheHive/issues/530)
- Naming inconsistencies in Live-Channel [\#531](https://github.com/TheHive-Project/TheHive/issues/531)
- PhishTank Cortex Tag is transparent [\#535](https://github.com/TheHive-Project/TheHive/issues/535)
- Cortex connection can fail without any error log [\#543](https://github.com/TheHive-Project/TheHive/issues/543)

**Pull requests:**

- Update spacing for elasticsearch section in docker-compose yml file [\#539](https://github.com/TheHive-Project/TheHive/pull/539)

**Closed issues:**

- Dropdown menu for case templates doesnt have scroll [\#541](https://github.com/TheHive-Project/TheHive/issues/541)

## [3.0.8](https://github.com/TheHive-Project/TheHive/milestone/31) (2018-04-04)

**Fixed bugs:**

- Job Analyzer is no longer named in 3.0.7 with Cortex2 [\#521](https://github.com/TheHive-Project/TheHive/issues/521)
- Error on displaying analyzers name in report template admin page [\#523](https://github.com/TheHive-Project/TheHive/issues/523)
- "Run all" in single observable context does not work [\#524](https://github.com/TheHive-Project/TheHive/issues/524)
- Session collision when TheHive & Cortex 2 share the same URL [\#525](https://github.com/TheHive-Project/TheHive/issues/525)
- Mini reports is not shown when Cortex 2 is used [\#526](https://github.com/TheHive-Project/TheHive/issues/526)

## [3.0.7](https://github.com/TheHive-Project/TheHive/milestone/30) (2018-03-29)

**Implemented enhancements:**

- Delete Case [\#100](https://github.com/TheHive-Project/TheHive/issues/100)

**Fixed bugs:**

- Can't save case template in 3.0.6 [\#502](https://github.com/TheHive-Project/TheHive/issues/502)
- Display only cortex servers available for each analyzer, in observable details page [\#513](https://github.com/TheHive-Project/TheHive/issues/513)

## [3.0.6](https://github.com/TheHive-Project/TheHive/milestone/29) (2018-03-02)

**Implemented enhancements:**

- Add compatibility with Cortex 2 [\#466](https://github.com/TheHive-Project/TheHive/issues/466)

**Fixed bugs:**

- Tasks are stripped when merging cases [\#489](https://github.com/TheHive-Project/TheHive/issues/489)

## [3.0.5](https://github.com/TheHive-Project/TheHive/milestone/28) (2018-02-08)

**Fixed bugs:**

- Importing Template Button Non-Functional [\#404](https://github.com/TheHive-Project/TheHive/issues/404)
- No reports available for "domain" type [\#469](https://github.com/TheHive-Project/TheHive/issues/469)

## [3.0.4](https://github.com/TheHive-Project/TheHive/milestone/27) (2018-02-08)

**Implemented enhancements:**

- Filter MISP Events Using MISP Tags & More Before Creating Alerts [\#370](https://github.com/TheHive-Project/TheHive/issues/370)
- Case metrics sort  [\#418](https://github.com/TheHive-Project/TheHive/issues/418)
- MISP feeds cause the growing of ES audit docs [\#450](https://github.com/TheHive-Project/TheHive/issues/450)
- Make counts on Counter dashboard's widget clickable [\#455](https://github.com/TheHive-Project/TheHive/issues/455)
- Make alerts searchable through the global search field [\#456](https://github.com/TheHive-Project/TheHive/issues/456)

**Fixed bugs:**

- Observable report taxonomies bug [\#409](https://github.com/TheHive-Project/TheHive/issues/409)
- Bug: Case metrics not shown when creating case from template [\#417](https://github.com/TheHive-Project/TheHive/issues/417)
- Refresh custom fields on open cases by background changes [\#440](https://github.com/TheHive-Project/TheHive/issues/440)
- Make dashboard donuts clickable [\#453](https://github.com/TheHive-Project/TheHive/issues/453)
- Fix link to default report templates [\#454](https://github.com/TheHive-Project/TheHive/issues/454)
- Type is not used when generating alert id [\#457](https://github.com/TheHive-Project/TheHive/issues/457)
- More than 20 users prevents assignment in tasks [\#459](https://github.com/TheHive-Project/TheHive/issues/459)
- Fix MISP export error dialog column's wrap [\#460](https://github.com/TheHive-Project/TheHive/issues/460)
- "too many substreams open" on alerts [\#462](https://github.com/TheHive-Project/TheHive/issues/462)
- Fix the alert bulk update timeline message [\#463](https://github.com/TheHive-Project/TheHive/issues/463)
- Remove uppercase filter on template name [\#464](https://github.com/TheHive-Project/TheHive/issues/464)

**Closed issues:**

- Add query capability to visualization elements [\#395](https://github.com/TheHive-Project/TheHive/issues/395)

## [2.13.3](https://github.com/TheHive-Project/TheHive/milestone/26) (2018-01-19)



## [3.0.3](https://github.com/TheHive-Project/TheHive/milestone/25) (2018-01-04)



## [3.0.2](https://github.com/TheHive-Project/TheHive/milestone/24) (2018-01-04)

**Implemented enhancements:**

- Can not configure ElasticSearch authentication [\#384](https://github.com/TheHive-Project/TheHive/issues/384)
- Add multiline/multi entity graph to dashboards [\#399](https://github.com/TheHive-Project/TheHive/issues/399)

**Fixed bugs:**

- "Mark as Sighted" Option not available for "File" observable type [\#400](https://github.com/TheHive-Project/TheHive/issues/400)

## [3.0.1](https://github.com/TheHive-Project/TheHive/milestone/23) (2017-12-13)

**Fixed bugs:**

- Error when configuring multiple ElasticSearch nodes [\#383](https://github.com/TheHive-Project/TheHive/issues/383)
- During migration, dashboards are not created [\#386](https://github.com/TheHive-Project/TheHive/issues/386)
- MISP Event Export Error [\#387](https://github.com/TheHive-Project/TheHive/issues/387)

## [3.0.0 (Cerana)](https://github.com/TheHive-Project/TheHive/milestone/6) (2017-12-06)

**Implemented enhancements:**

- Feature Request: Webhooks [\#20](https://github.com/TheHive-Project/TheHive/issues/20)
- Display Cortex Version, Instance Name, Status and Available Analyzers [\#130](https://github.com/TheHive-Project/TheHive/issues/130)
- Show already known observables in Import MISP Events preview window [\#137](https://github.com/TheHive-Project/TheHive/issues/137)
- Assign default metric values [\#176](https://github.com/TheHive-Project/TheHive/issues/176)
- Export Statistics/Metrics [\#197](https://github.com/TheHive-Project/TheHive/issues/197)
- Statistics: Observables and IOC over time [\#215](https://github.com/TheHive-Project/TheHive/issues/215)
- Templates can not be cloned [\#226](https://github.com/TheHive-Project/TheHive/issues/226)
- Alerts in Statistics [\#274](https://github.com/TheHive-Project/TheHive/issues/274)
- Statistics - Saved Filters [\#279](https://github.com/TheHive-Project/TheHive/issues/279)
- Add health check in status API [\#306](https://github.com/TheHive-Project/TheHive/issues/306)
- Export and Import Case Templates [\#310](https://github.com/TheHive-Project/TheHive/issues/310)
- Dynamic dashboard [\#312](https://github.com/TheHive-Project/TheHive/issues/312)
- Export to MISP: add TLP [\#314](https://github.com/TheHive-Project/TheHive/issues/314)
- Deleted cases showing in statistics [\#317](https://github.com/TheHive-Project/TheHive/issues/317)
- Keep the alert date when creating a case from it [\#320](https://github.com/TheHive-Project/TheHive/issues/320)
- [Minor] Add user dialog title issue [\#345](https://github.com/TheHive-Project/TheHive/issues/345)
- Display more than 10 users per page and sort them by alphanumerical order [\#346](https://github.com/TheHive-Project/TheHive/issues/346)
- Remove the From prefix and template suffix around a template name in the New Case menu [\#348](https://github.com/TheHive-Project/TheHive/issues/348)
- Add Autonomous Systems to the Default Datatype List [\#359](https://github.com/TheHive-Project/TheHive/issues/359)
- Set task assignee in case template [\#362](https://github.com/TheHive-Project/TheHive/issues/362)
- Alert id should not be used to build case title when using case templates [\#364](https://github.com/TheHive-Project/TheHive/issues/364)
- Add a sighted flag for IOCs [\#365](https://github.com/TheHive-Project/TheHive/issues/365)
- Add the Ability to Import and Export Case Templates [\#369](https://github.com/TheHive-Project/TheHive/issues/369)
- Assign default values to case templates' custom fields [\#375](https://github.com/TheHive-Project/TheHive/issues/375)

**Fixed bugs:**

- Merge of cases overrides task log owners [\#303](https://github.com/TheHive-Project/TheHive/issues/303)
- Validate alert's TLP and severity attributes values [\#326](https://github.com/TheHive-Project/TheHive/issues/326)
- Share a case if MISP is not enabled raise an error [\#349](https://github.com/TheHive-Project/TheHive/issues/349)
- [Bug] Merging an alert into case with duplicate artifacts does not merge descriptions [\#357](https://github.com/TheHive-Project/TheHive/issues/357)

**Closed issues:**

- Single Sign-On with X.509 certificates [\#297](https://github.com/TheHive-Project/TheHive/issues/297)
- Remove the deprecated "user" property [\#316](https://github.com/TheHive-Project/TheHive/issues/316)
- caseTemplate should be kept when creating a case from a template [\#325](https://github.com/TheHive-Project/TheHive/issues/325)

## [2.13.2](https://github.com/TheHive-Project/TheHive/milestone/22) (2017-11-08)

**Fixed bugs:**

- Error on custom fields format when merging cases [\#331](https://github.com/TheHive-Project/TheHive/issues/331)
- Statistics on metrics doesn't work [\#342](https://github.com/TheHive-Project/TheHive/issues/342)
- Deleted Observables, Show up on the statistics tab under Observables by Type [\#343](https://github.com/TheHive-Project/TheHive/issues/343)
- Incorrect stats: non-IOC observables counted as IOC and IOC word displayed twice [\#347](https://github.com/TheHive-Project/TheHive/issues/347)
- Security issue on Play 2.6.5 [\#356](https://github.com/TheHive-Project/TheHive/issues/356)

## [2.13.1](https://github.com/TheHive-Project/TheHive/milestone/21) (2017-09-18)

**Fixed bugs:**

- Tasks Tab Elasticsearch exception: Fielddata is disabled on text fields by default. Set fielddata=true on [title] [\#311](https://github.com/TheHive-Project/TheHive/issues/311)

## [2.13.0](https://github.com/TheHive-Project/TheHive/milestone/13) (2017-09-15)

**Implemented enhancements:**

- Export cases in MISP events [\#52](https://github.com/TheHive-Project/TheHive/issues/52)
- Specify multiple AD servers in TheHive configuration [\#231](https://github.com/TheHive-Project/TheHive/issues/231)
- Alert Pane: Catch Incorrect Keywords [\#241](https://github.com/TheHive-Project/TheHive/issues/241)
- Fine grained user permissions for API access [\#263](https://github.com/TheHive-Project/TheHive/issues/263)
- Add Support for Play 2.6.x and Elasticsearch 5.x [\#275](https://github.com/TheHive-Project/TheHive/issues/275)
- Add basic authentication to Stream API [\#291](https://github.com/TheHive-Project/TheHive/issues/291)
- Add a basic support for webhooks [\#293](https://github.com/TheHive-Project/TheHive/issues/293)
- Improve the content of alert flow items [\#304](https://github.com/TheHive-Project/TheHive/issues/304)
- Group ownership in Docker image prevents running on OpenShift [\#307](https://github.com/TheHive-Project/TheHive/issues/307)

**Fixed bugs:**

- A colon punctuation mark in a search query results in 500 [\#285](https://github.com/TheHive-Project/TheHive/issues/285)
- File name is not displayed in observable conflict dialog [\#295](https://github.com/TheHive-Project/TheHive/issues/295)
- Undefined threat level from MISP events becomes severity "4" [\#300](https://github.com/TheHive-Project/TheHive/issues/300)
- Download attachment with non-latin filename [\#302](https://github.com/TheHive-Project/TheHive/issues/302)

**Closed issues:**

- Elasticsearch 5.x roadmap? [\#82](https://github.com/TheHive-Project/TheHive/issues/82)
- Threat level/severity code inverted between The Hive and MISP [\#292](https://github.com/TheHive-Project/TheHive/issues/292)

## [2.12.1](https://github.com/TheHive-Project/TheHive/milestone/15) (2017-08-24)

**Implemented enhancements:**

- Merging alert into existing case does not merge alert description into case description [\#255](https://github.com/TheHive-Project/TheHive/issues/255)
- Fix warnings in debian package [\#267](https://github.com/TheHive-Project/TheHive/issues/267)

**Fixed bugs:**

- Renaming of users does not work [\#249](https://github.com/TheHive-Project/TheHive/issues/249)
- TheHive doesn't send the file name to Cortex [\#254](https://github.com/TheHive-Project/TheHive/issues/254)
- Add multiple attachments in a single task log doesn't work [\#257](https://github.com/TheHive-Project/TheHive/issues/257)
- Can't get logs of a task via API [\#259](https://github.com/TheHive-Project/TheHive/issues/259)
- API: cannot create alert if one alert artifact contains the IOC field set [\#268](https://github.com/TheHive-Project/TheHive/issues/268)
- Closing a case with an open task does not dismiss task in "My tasks" [\#269](https://github.com/TheHive-Project/TheHive/issues/269)
- Case similarity reports merged cases [\#272](https://github.com/TheHive-Project/TheHive/issues/272)

## [2.12.0](https://github.com/TheHive-Project/TheHive/milestone/11) (2017-07-06)

**Implemented enhancements:**

- Custom fields for case template [\#12](https://github.com/TheHive-Project/TheHive/issues/12)
- Display short reports on the Observables tab [\#131](https://github.com/TheHive-Project/TheHive/issues/131)
- Ability to Reopen Tasks [\#156](https://github.com/TheHive-Project/TheHive/issues/156)
- Choose case template while importing events from MISP [\#175](https://github.com/TheHive-Project/TheHive/issues/175)
- Specifying tags on statistics page or performing a search [\#186](https://github.com/TheHive-Project/TheHive/issues/186)
- Observable analyzers view reports. [\#191](https://github.com/TheHive-Project/TheHive/issues/191)
- Open External Links in New Tab [\#228](https://github.com/TheHive-Project/TheHive/issues/228)
- Show case status and category (FP, TP, IND) in related cases  [\#229](https://github.com/TheHive-Project/TheHive/issues/229)
- Alert Preview and management improvements [\#232](https://github.com/TheHive-Project/TheHive/issues/232)
- More options to sort cases [\#243](https://github.com/TheHive-Project/TheHive/issues/243)
- Sort the analyzers list in observable details page [\#245](https://github.com/TheHive-Project/TheHive/issues/245)
- Use local font files [\#250](https://github.com/TheHive-Project/TheHive/issues/250)

**Fixed bugs:**

- report status not updated after finish [\#212](https://github.com/TheHive-Project/TheHive/issues/212)
- Search do not work with non-latin characters [\#223](https://github.com/TheHive-Project/TheHive/issues/223)
- Alert can contain inconsistent data [\#234](https://github.com/TheHive-Project/TheHive/issues/234)
- files in alerts are limited to 32kB [\#237](https://github.com/TheHive-Project/TheHive/issues/237)
- Alerting Panel: Typo Correction [\#240](https://github.com/TheHive-Project/TheHive/issues/240)
- Sorting alerts by severity fails [\#242](https://github.com/TheHive-Project/TheHive/issues/242)
- Fix case metrics malformed definitions [\#248](https://github.com/TheHive-Project/TheHive/issues/248)
- A locked user can use the API to create / delete / list cases (and more) [\#251](https://github.com/TheHive-Project/TheHive/issues/251)

## [2.11.3](https://github.com/TheHive-Project/TheHive/milestone/14) (2017-06-14)

**Fixed bugs:**

- MISP synchronization doesn't retrieve all events [\#236](https://github.com/TheHive-Project/TheHive/issues/236)
- Problem Start TheHive on Ubuntu 16.04 [\#238](https://github.com/TheHive-Project/TheHive/issues/238)
- Unable to add tasks to case template [\#239](https://github.com/TheHive-Project/TheHive/issues/239)

## [2.11.2](https://github.com/TheHive-Project/TheHive/milestone/12) (2017-05-31)

**Implemented enhancements:**

- Show case severity in lists [\#188](https://github.com/TheHive-Project/TheHive/issues/188)
- Add Description Field to Alert Preview Modal [\#218](https://github.com/TheHive-Project/TheHive/issues/218)
- Visually distinguish between analyzed and non analyzer observables [\#224](https://github.com/TheHive-Project/TheHive/issues/224)

**Fixed bugs:**

- Cortex jobs from thehive fail silently [\#219](https://github.com/TheHive-Project/TheHive/issues/219)
- MISP synchronization - Alerts are wrongly updated [\#220](https://github.com/TheHive-Project/TheHive/issues/220)
- MISP synchronization - attributes are not retrieve [\#221](https://github.com/TheHive-Project/TheHive/issues/221)

## [2.11.1](https://github.com/TheHive-Project/TheHive/milestone/10) (2017-05-17)

**Implemented enhancements:**

- Merge Duplicate Tasks during Case Merge [\#180](https://github.com/TheHive-Project/TheHive/issues/180)
- Show available reports number for each observable [\#211](https://github.com/TheHive-Project/TheHive/issues/211)

**Fixed bugs:**

- Error updating case templates [\#204](https://github.com/TheHive-Project/TheHive/issues/204)
- Observable of merged cased might have duplicate tags [\#205](https://github.com/TheHive-Project/TheHive/issues/205)
- Case templates not applied when converting an alert to a case [\#206](https://github.com/TheHive-Project/TheHive/issues/206)

**Closed issues:**

- No API Alert documentation [\#203](https://github.com/TheHive-Project/TheHive/issues/203)

## [2.11.0](https://github.com/TheHive-Project/TheHive/milestone/4) (2017-05-12)

**Implemented enhancements:**

- Reordering Tasks [\#21](https://github.com/TheHive-Project/TheHive/issues/21)
- MISP import filter / filtering of events [\#86](https://github.com/TheHive-Project/TheHive/issues/86)
- Ignored MISP events are no longer visible and cannot be imported [\#107](https://github.com/TheHive-Project/TheHive/issues/107)
- Feature request: Autocomplete tags [\#119](https://github.com/TheHive-Project/TheHive/issues/119)
- Improve logs browsing [\#128](https://github.com/TheHive-Project/TheHive/issues/128)
- Proxy authentication [\#143](https://github.com/TheHive-Project/TheHive/issues/143)
- Add support of case template in back-end API [\#144](https://github.com/TheHive-Project/TheHive/issues/144)
- Refresh the UI's skin [\#145](https://github.com/TheHive-Project/TheHive/issues/145)
- Disable field autocomplete on the login form [\#146](https://github.com/TheHive-Project/TheHive/issues/146)
- Connect to Cortex instance via proxy [\#147](https://github.com/TheHive-Project/TheHive/issues/147)
- Add pagination component at the top of all the data lists [\#151](https://github.com/TheHive-Project/TheHive/issues/151)
- Show severity on the "Cases Page" [\#165](https://github.com/TheHive-Project/TheHive/issues/165)
- Update the datalist filter previews to display meaningful values [\#166](https://github.com/TheHive-Project/TheHive/issues/166)
- Make the flow collapsible, in case details page [\#167](https://github.com/TheHive-Project/TheHive/issues/167)
- Implement the alerting framework feature [\#170](https://github.com/TheHive-Project/TheHive/issues/170)
- Connect to Cortex protected by Basic Auth [\#173](https://github.com/TheHive-Project/TheHive/issues/173)
- Cannot distinguish which analysers run on which cortex instance [\#179](https://github.com/TheHive-Project/TheHive/issues/179)
- Add support to .deb and .rpm package generation [\#193](https://github.com/TheHive-Project/TheHive/issues/193)
- Sort the list of report templates [\#195](https://github.com/TheHive-Project/TheHive/issues/195)
- TheHive send to many information to Cortex when an analyze is requested [\#196](https://github.com/TheHive-Project/TheHive/issues/196)
- Display the logos of the integrated external services [\#198](https://github.com/TheHive-Project/TheHive/issues/198)

**Fixed bugs:**

- Job status refresh [\#171](https://github.com/TheHive-Project/TheHive/issues/171)
- Duplicate HTTP calls in case page [\#187](https://github.com/TheHive-Project/TheHive/issues/187)
- Fix the success message when running a set of analyzers [\#199](https://github.com/TheHive-Project/TheHive/issues/199)
- Authentication fails with wrong message if database migration is needed [\#200](https://github.com/TheHive-Project/TheHive/issues/200)

**Closed issues:**

- MISP event filter require manual escapes [\#87](https://github.com/TheHive-Project/TheHive/issues/87)
- Scala code cleanup [\#153](https://github.com/TheHive-Project/TheHive/issues/153)

## [2.10.2](https://github.com/TheHive-Project/TheHive/milestone/8) (2017-04-18)

**Implemented enhancements:**

- Persistence for task viewing options [\#157](https://github.com/TheHive-Project/TheHive/issues/157)
- Add CSRF protection [\#158](https://github.com/TheHive-Project/TheHive/issues/158)
- Run all analyzers on multiple observables from observables view [\#174](https://github.com/TheHive-Project/TheHive/issues/174)

**Fixed bugs:**

- Pagination does not work with 100 results per page [\#152](https://github.com/TheHive-Project/TheHive/issues/152)
- Secure the usage of angular-ui-notification library [\#159](https://github.com/TheHive-Project/TheHive/issues/159)
- Disable readonly access to admin pages, for users without 'admin' role [\#160](https://github.com/TheHive-Project/TheHive/issues/160)
- Unauthenticated access to some pages doesn't redirect to login page [\#161](https://github.com/TheHive-Project/TheHive/issues/161)
- MISP import fails [\#169](https://github.com/TheHive-Project/TheHive/issues/169)

**Closed issues:**

- Observable Tags not displayed in 2.10.1 [\#155](https://github.com/TheHive-Project/TheHive/issues/155)

## [2.10.1](https://github.com/TheHive-Project/TheHive/milestone/3) (2017-03-08)

**Implemented enhancements:**

- Upgrade to the last version of UI-Bootstrap UI library [\#79](https://github.com/TheHive-Project/TheHive/issues/79)
- Misleading MISP Event Date and Time [\#101](https://github.com/TheHive-Project/TheHive/issues/101)
- Make The Hive working on any URL path and not only / [\#114](https://github.com/TheHive-Project/TheHive/issues/114)
- Disable buttons in MISP event's preview dialog [\#115](https://github.com/TheHive-Project/TheHive/issues/115)
- Add pagination component at the top of the task log [\#116](https://github.com/TheHive-Project/TheHive/issues/116)
- Restyle avatar's upload button [\#126](https://github.com/TheHive-Project/TheHive/issues/126)
- Display a warning when trying to merge an already merged case [\#129](https://github.com/TheHive-Project/TheHive/issues/129)
- Typo in quick filters [\#134](https://github.com/TheHive-Project/TheHive/issues/134)
- Remove duplicate stream callbacks registration [\#138](https://github.com/TheHive-Project/TheHive/issues/138)
- Remove the "Run all analyzers" option from observables list [\#141](https://github.com/TheHive-Project/TheHive/issues/141)

**Fixed bugs:**

- Observables password hint does not reflect backend change [\#83](https://github.com/TheHive-Project/TheHive/issues/83)
- Cannot add an observable which datatype has been added by an admin [\#106](https://github.com/TheHive-Project/TheHive/issues/106)
- Open log in new windows [\#108](https://github.com/TheHive-Project/TheHive/issues/108)
- Web UI doesn't refresh once a report template is deleted [\#113](https://github.com/TheHive-Project/TheHive/issues/113)
- Case merge does not close tasks in merged cases [\#118](https://github.com/TheHive-Project/TheHive/issues/118)
- Flow is not shown [\#127](https://github.com/TheHive-Project/TheHive/issues/127)
- Fix a JS issue related to inactivity dialog [\#139](https://github.com/TheHive-Project/TheHive/issues/139)
- 401 HTTP responses don't trigger redirection to login page [\#140](https://github.com/TheHive-Project/TheHive/issues/140)
- Fix OTXQuery report template [\#142](https://github.com/TheHive-Project/TheHive/issues/142)

## [2.10.0](https://github.com/TheHive-Project/TheHive/milestone/2) (2017-02-03)

**Implemented enhancements:**

- Newly created case template not visible in NEW case until logout/login [\#26](https://github.com/TheHive-Project/TheHive/issues/26)
- Make release process easier [\#28](https://github.com/TheHive-Project/TheHive/issues/28)
- Changeable case owner [\#30](https://github.com/TheHive-Project/TheHive/issues/30)
- Externalize observable analysis [\#53](https://github.com/TheHive-Project/TheHive/issues/53)
- Load the Current Cases View when Closing a Case [\#61](https://github.com/TheHive-Project/TheHive/issues/61)
- When closing a task, close the associated tab as well [\#66](https://github.com/TheHive-Project/TheHive/issues/66)
- Allow (un)set observable as IOC from the observable's page [\#68](https://github.com/TheHive-Project/TheHive/issues/68)
- Use avatars in user profiles [\#69](https://github.com/TheHive-Project/TheHive/issues/69)
- Feature Request - Add Case Statistics by Severity [\#70](https://github.com/TheHive-Project/TheHive/issues/70)
- Improve cases listing page [\#76](https://github.com/TheHive-Project/TheHive/issues/76)

**Fixed bugs:**

- Missing markdown editor in case close dialog [\#42](https://github.com/TheHive-Project/TheHive/issues/42)
- Make sure to clear new task log editor [\#57](https://github.com/TheHive-Project/TheHive/issues/57)
- MISP events counter is not refreshed [\#58](https://github.com/TheHive-Project/TheHive/issues/58)
- Locked users are still able to log in [\#59](https://github.com/TheHive-Project/TheHive/issues/59)
- Assign a default role to new users and remove the ability to assign empty roles [\#60](https://github.com/TheHive-Project/TheHive/issues/60)
- Don't use deleted obserables to link cases [\#62](https://github.com/TheHive-Project/TheHive/issues/62)
- Add an already exist observable returns an unexpected error [\#63](https://github.com/TheHive-Project/TheHive/issues/63)
- Task descriptions from case templates are not applied [\#65](https://github.com/TheHive-Project/TheHive/issues/65)
- Locked users cannot be assignee of cases [\#77](https://github.com/TheHive-Project/TheHive/issues/77)
- User is not notified on MISP error [\#88](https://github.com/TheHive-Project/TheHive/issues/88)
- Case TLP should be set to AMBER by default [\#96](https://github.com/TheHive-Project/TheHive/issues/96)
- Bug related case [\#97](https://github.com/TheHive-Project/TheHive/issues/97)
- Hippocampe Analyzer [\#104](https://github.com/TheHive-Project/TheHive/issues/104)
- Template Limit Bug [\#105](https://github.com/TheHive-Project/TheHive/issues/105)

**Pull requests:**

- New analyzer to query PhishTank for a URL [\#27](https://github.com/TheHive-Project/TheHive/pull/27)
- AlienVault OTX Analyzer [\#39](https://github.com/TheHive-Project/TheHive/pull/39)

**Closed issues:**

- OTX Analyzer [\#32](https://github.com/TheHive-Project/TheHive/issues/32)
- PhishTank Analyzer [\#40](https://github.com/TheHive-Project/TheHive/issues/40)
- Unable to use SSL on AD auth [\#50](https://github.com/TheHive-Project/TheHive/issues/50)
- Create an analyzer to get information about PE file [\#51](https://github.com/TheHive-Project/TheHive/issues/51)
- Add support for more filetypes to PE_info analyser [\#54](https://github.com/TheHive-Project/TheHive/issues/54)
- Database schema update (v8) [\#67](https://github.com/TheHive-Project/TheHive/issues/67)

## [2.9.2](https://github.com/TheHive-Project/TheHive/milestone/5) (2017-01-19)

**Fixed bugs:**

- docker image: $.post(...).success is not a function [\#95](https://github.com/TheHive-Project/TheHive/issues/95)

## [2.9.1](https://github.com/TheHive-Project/TheHive/milestone/1) (2016-11-28)

**Implemented enhancements:**

- Case merging [\#14](https://github.com/TheHive-Project/TheHive/issues/14)
- Don't update imported case from MISP if it is deleted or merged [\#22](https://github.com/TheHive-Project/TheHive/issues/22)
- MaxMind Analyzer 'Short Report' has hard-coded language [\#23](https://github.com/TheHive-Project/TheHive/issues/23)
- New analyzer to check URL categories [\#24](https://github.com/TheHive-Project/TheHive/pull/24)
- Inconsistent wording between the login and user management pages [\#44](https://github.com/TheHive-Project/TheHive/issues/44)
- Update logo and favicon [\#45](https://github.com/TheHive-Project/TheHive/issues/45)

**Fixed bugs:**

- Tags not saving when creating observable. [\#4](https://github.com/TheHive-Project/TheHive/issues/4)
- chrome on os x - header alignment [\#5](https://github.com/TheHive-Project/TheHive/issues/5)
- Metric Labels Not Showing in Case View [\#10](https://github.com/TheHive-Project/TheHive/issues/10)
- Description becomes empty when you cancel an edition [\#13](https://github.com/TheHive-Project/TheHive/issues/13)
- The Action button of observables list is blank [\#15](https://github.com/TheHive-Project/TheHive/issues/15)
- Phantom tabs [\#18](https://github.com/TheHive-Project/TheHive/issues/18)
- MISP event parsing error when it doesn't contain any attribute [\#25](https://github.com/TheHive-Project/TheHive/issues/25)
- Systemd startup script does not work [\#29](https://github.com/TheHive-Project/TheHive/issues/29)
- NPE occurs at startup if conf directory doesn't exists [\#41](https://github.com/TheHive-Project/TheHive/issues/41)
