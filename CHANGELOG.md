# Change Log

## [3.0.5](https://github.com/TheHive-Project/TheHive/tree/3.0.5) (2018-02-08)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.4...3.0.5)

**Fixed bugs:**

 - Importing Template Button Non-Functional bug [\#404](https://github.com/TheHive-Project/TheHive/issues/404)
 - No reports available for "domain" type bug [\#409](https://github.com/TheHive-Project/TheHive/issues/409)

## [3.0.4](https://github.com/TheHive-Project/TheHive/tree/3.0.4) (2018-02-05)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.3...3.0.4)

**Implemented enhancements:**

- Add compatibility with Cortex 2 [\#466](https://github.com/TheHive-Project/TheHive/issues/466)
- Make alerts searchable through the global search field [\#456](https://github.com/TheHive-Project/TheHive/issues/456)
- Make counts on Counter dashboard's widget clickable [\#455](https://github.com/TheHive-Project/TheHive/issues/455)
- MISP feeds cause the growing of ES audit docs [\#450](https://github.com/TheHive-Project/TheHive/issues/450)
- Case metrics sort  [\#418](https://github.com/TheHive-Project/TheHive/issues/418)
- Filter MISP Events Using MISP Tags & More Before Creating Alerts [\#370](https://github.com/TheHive-Project/TheHive/issues/370)
- OAuth2 single sign-on implementation \(BE + FE\) [\#430](https://github.com/TheHive-Project/TheHive/pull/430) ([saibot94](https://github.com/saibot94))

**Fixed bugs:**

- Remove uppercase filter on template name [\#464](https://github.com/TheHive-Project/TheHive/issues/464)
- Fix the alert bulk update timeline message [\#463](https://github.com/TheHive-Project/TheHive/issues/463)
- "too many substreams open" on alerts [\#462](https://github.com/TheHive-Project/TheHive/issues/462)
- Fix MISP export error dialog column's wrap [\#460](https://github.com/TheHive-Project/TheHive/issues/460)
- More than 20 users prevents assignment in tasks [\#459](https://github.com/TheHive-Project/TheHive/issues/459)
- Type is not used when generating alert id [\#457](https://github.com/TheHive-Project/TheHive/issues/457)
- Fix link to default report templates [\#454](https://github.com/TheHive-Project/TheHive/issues/454)
- Make dashboard donuts clickable [\#453](https://github.com/TheHive-Project/TheHive/issues/453)
- Refresh custom fields on open cases by background changes [\#440](https://github.com/TheHive-Project/TheHive/issues/440)
- Bug: Case metrics not shown when creating case from template [\#417](https://github.com/TheHive-Project/TheHive/issues/417)
- Observable report taxonomies bug [\#409](https://github.com/TheHive-Project/TheHive/issues/409)

**Closed issues:**

- GET request with Content-Type ends up in HTTP 400 [\#438](https://github.com/TheHive-Project/TheHive/issues/438)
- Feature Request: Ability to bulk upload files as observables. [\#435](https://github.com/TheHive-Project/TheHive/issues/435)
- Add metadata to MISP event when exporting case from TheHive [\#433](https://github.com/TheHive-Project/TheHive/issues/433)
- How to limit by date amount of events pulled from MISP initially? [\#432](https://github.com/TheHive-Project/TheHive/issues/432)

## [3.0.3](https://github.com/TheHive-Project/TheHive/tree/3.0.3) (2018-01-10)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.2...3.0.3)

**Fixed bugs:**

- THP-SEC-ADV-2017-001: Privilege Escalation in all Versions of TheHive [\#408](https://github.com/TheHive-Project/TheHive/issues/408)

## [3.0.2](https://github.com/TheHive-Project/TheHive/tree/3.0.2) (2017-12-20)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.1...3.0.2)

**Implemented enhancements:**

- Add multiline/multi entity graph to dashboards [\#399](https://github.com/TheHive-Project/TheHive/issues/399)
- Can not configure ElasticSearch authentication [\#384](https://github.com/TheHive-Project/TheHive/issues/384)

**Fixed bugs:**

- "Mark as Sighted" Option not available for "File" observable type [\#400](https://github.com/TheHive-Project/TheHive/issues/400)

## [3.0.1](https://github.com/TheHive-Project/TheHive/tree/3.0.1) (2017-12-07)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.0...3.0.1)

**Fixed bugs:**

- MISP Event Export Error [\#387](https://github.com/TheHive-Project/TheHive/issues/387)
- During migration, dashboards are not created [\#386](https://github.com/TheHive-Project/TheHive/issues/386)
- Error when configuring multiple ElasticSearch nodes [\#383](https://github.com/TheHive-Project/TheHive/issues/383)

## [3.0.0](https://github.com/TheHive-Project/TheHive/tree/3.0.0) (2017-12-05)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.13.2...3.0.0)

**Implemented enhancements:**

- Assign default values to case templates' custom fields [\#375](https://github.com/TheHive-Project/TheHive/issues/375)
- Add the Ability to Import and Export Case Templates [\#369](https://github.com/TheHive-Project/TheHive/issues/369)
- Add a sighted flag for IOCs [\#365](https://github.com/TheHive-Project/TheHive/issues/365)
- Alert id should not be used to build case title when using case templates [\#364](https://github.com/TheHive-Project/TheHive/issues/364)
- Set task assignee in case template [\#362](https://github.com/TheHive-Project/TheHive/issues/362)
- Add Autonomous Systems to the Default Datatype List [\#359](https://github.com/TheHive-Project/TheHive/issues/359)
- Display more than 10 users per page and sort them by alphanumerical order [\#346](https://github.com/TheHive-Project/TheHive/issues/346)
- \[Minor\] Add user dialog title issue [\#345](https://github.com/TheHive-Project/TheHive/issues/345)
- Deleted cases showing in statistics [\#317](https://github.com/TheHive-Project/TheHive/issues/317)
- Dynamic dashboard [\#312](https://github.com/TheHive-Project/TheHive/issues/312)
- Add health check in status API [\#306](https://github.com/TheHive-Project/TheHive/issues/306)
- Alerts in Statistics [\#274](https://github.com/TheHive-Project/TheHive/issues/274)
- Statistics: Observables and IOC over time [\#215](https://github.com/TheHive-Project/TheHive/issues/215)
- Export Statistics/Metrics [\#197](https://github.com/TheHive-Project/TheHive/issues/197)
- Msg\_Parser analyser show for all files [\#184](https://github.com/TheHive-Project/TheHive/issues/184)
- Assign default metric values [\#176](https://github.com/TheHive-Project/TheHive/issues/176)
- Display Cortex Version, Instance Name, Status and Available Analyzers [\#130](https://github.com/TheHive-Project/TheHive/issues/130)
- Feature Request: Webhooks [\#20](https://github.com/TheHive-Project/TheHive/issues/20)
- Remove the From prefix and template suffix around a template name in the New Case menu [\#348](https://github.com/TheHive-Project/TheHive/issues/348)
- Keep the alert date when creating a case from it [\#320](https://github.com/TheHive-Project/TheHive/issues/320)
- Export to MISP: add TLP [\#314](https://github.com/TheHive-Project/TheHive/issues/314)
- Show already known observables in Import MISP Events preview window [\#137](https://github.com/TheHive-Project/TheHive/issues/137)

**Fixed bugs:**

- The misp \> instance name \> tags parameter is not honored when importing MISP events [\#373](https://github.com/TheHive-Project/TheHive/issues/373)
- \[Bug\] Merging an alert into case with duplicate artifacts does not merge descriptions [\#357](https://github.com/TheHive-Project/TheHive/issues/357)
- Share a case if MISP is not enabled raise an error [\#349](https://github.com/TheHive-Project/TheHive/issues/349)
- Validate alert's TLP and severity attributes values [\#326](https://github.com/TheHive-Project/TheHive/issues/326)
- Merge of cases overrides task log owners [\#303](https://github.com/TheHive-Project/TheHive/issues/303)

**Closed issues:**

- MISP Connection Error with Cortex/HIVE [\#371](https://github.com/TheHive-Project/TheHive/issues/371)
- Single Sign-On with X.509 certificates [\#297](https://github.com/TheHive-Project/TheHive/issues/297)
- Remove the deprecated "user" property [\#316](https://github.com/TheHive-Project/TheHive/issues/316)
- Run observable analyzers through API [\#308](https://github.com/TheHive-Project/TheHive/issues/308)

**Merged pull requests:**

- typos and improvements to text [\#355](https://github.com/TheHive-Project/TheHive/pull/355) ([steoleary](https://github.com/steoleary))
- Correct typo [\#353](https://github.com/TheHive-Project/TheHive/pull/353) ([arnydo](https://github.com/arnydo))

## [2.13.2](https://github.com/TheHive-Project/TheHive/tree/2.13.2) (2017-10-24)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.13.1...2.13.2)

**Fixed bugs:**

- Security issue on Play 2.6.5 [\#356](https://github.com/TheHive-Project/TheHive/issues/356)
- Incorrect stats: non-IOC observables counted as IOC and IOC word displayed twice [\#347](https://github.com/TheHive-Project/TheHive/issues/347)
- Deleted Observables, Show up on the statistics tab under Observables by Type [\#343](https://github.com/TheHive-Project/TheHive/issues/343)
- Statistics on metrics doesn't work [\#342](https://github.com/TheHive-Project/TheHive/issues/342)
- Error on custom fields format when merging cases [\#331](https://github.com/TheHive-Project/TheHive/issues/331)

## [2.13.1](https://github.com/TheHive-Project/TheHive/tree/2.13.1) (2017-09-18)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.13.0...2.13.1)

**Fixed bugs:**

- Tasks Tab Elasticsearch exception: Fielddata is disabled on text fields by default. Set fielddata=true on \[title\] [\#311](https://github.com/TheHive-Project/TheHive/issues/311)

## [2.13.0](https://github.com/TheHive-Project/TheHive/tree/2.13.0) (2017-09-15)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.12.1...2.13.0)

**Implemented enhancements:**

- Group ownership in Docker image prevents running on OpenShift [\#307](https://github.com/TheHive-Project/TheHive/issues/307)
- Improve the content of alert flow items [\#304](https://github.com/TheHive-Project/TheHive/issues/304)
- Add a basic support for webhooks [\#293](https://github.com/TheHive-Project/TheHive/issues/293)
- Add basic authentication to Stream API [\#291](https://github.com/TheHive-Project/TheHive/issues/291)
- Add Support for Play 2.6.x and Elasticsearch 5.x [\#275](https://github.com/TheHive-Project/TheHive/issues/275)
- Fine grained user permissions for API access [\#263](https://github.com/TheHive-Project/TheHive/issues/263)
- Alert Pane: Catch Incorrect Keywords [\#241](https://github.com/TheHive-Project/TheHive/issues/241)
- Specify multiple AD servers in TheHive configuration [\#231](https://github.com/TheHive-Project/TheHive/issues/231)
- Export cases in MISP events [\#52](https://github.com/TheHive-Project/TheHive/issues/52)

**Fixed bugs:**

- Download attachment with non-latin filename [\#302](https://github.com/TheHive-Project/TheHive/issues/302)
- Undefined threat level from MISP events becomes severity "4" [\#300](https://github.com/TheHive-Project/TheHive/issues/300)
- File name is not displayed in observable conflict dialog [\#295](https://github.com/TheHive-Project/TheHive/issues/295)
- A colon punctuation mark in a search query results in 500 [\#285](https://github.com/TheHive-Project/TheHive/issues/285)
- Previewing alerts fails with "too many substreams open" due to case similarity process [\#280](https://github.com/TheHive-Project/TheHive/issues/280)

**Closed issues:**

- Threat level/severity code inverted between The Hive and MISP [\#292](https://github.com/TheHive-Project/TheHive/issues/292)

## [2.12.1](https://github.com/TheHive-Project/TheHive/tree/2.12.1) (2017-08-01)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.12.0...2.12.1)

**Implemented enhancements:**

- Fix warnings in debian package [\#267](https://github.com/TheHive-Project/TheHive/issues/267)
- Merging alert into existing case does not merge alert description into case description [\#255](https://github.com/TheHive-Project/TheHive/issues/255)

**Fixed bugs:**

- Cortex Connector Not Found [\#256](https://github.com/TheHive-Project/TheHive/issues/256)
- Case similarity reports merged cases [\#272](https://github.com/TheHive-Project/TheHive/issues/272)
- Closing a case with an open task does not dismiss task in "My tasks" [\#269](https://github.com/TheHive-Project/TheHive/issues/269)
- API: cannot create alert if one alert artifact contains the IOC field set [\#268](https://github.com/TheHive-Project/TheHive/issues/268)
- Can't get logs of a task via API [\#259](https://github.com/TheHive-Project/TheHive/issues/259)
- Add multiple attachments in a single task log doesn't work [\#257](https://github.com/TheHive-Project/TheHive/issues/257)
- TheHive doesn't send the file name to Cortex [\#254](https://github.com/TheHive-Project/TheHive/issues/254)
- Renaming of users does not work [\#249](https://github.com/TheHive-Project/TheHive/issues/249)

## [2.12.0](https://github.com/TheHive-Project/TheHive/tree/2.12.0) (2017-07-04)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.11.3...2.12.0)

**Implemented enhancements:**

- Use local font files [\#250](https://github.com/TheHive-Project/TheHive/issues/250)
- Sort the analyzers list in observable details page [\#245](https://github.com/TheHive-Project/TheHive/issues/245)
- More options to sort cases [\#243](https://github.com/TheHive-Project/TheHive/issues/243)
- Alert Preview and management improvements [\#232](https://github.com/TheHive-Project/TheHive/issues/232)
- Show case status and category \(FP, TP, IND\) in related cases  [\#229](https://github.com/TheHive-Project/TheHive/issues/229)
- Open External Links in New Tab [\#228](https://github.com/TheHive-Project/TheHive/issues/228)
- Observable analyzers view reports. [\#191](https://github.com/TheHive-Project/TheHive/issues/191)
- Specifying tags on statistics page or performing a search [\#186](https://github.com/TheHive-Project/TheHive/issues/186)
- Choose case template while importing events from MISP [\#175](https://github.com/TheHive-Project/TheHive/issues/175)
- Ability to Reopen Tasks [\#156](https://github.com/TheHive-Project/TheHive/issues/156)
- Display short reports on the Observables tab [\#131](https://github.com/TheHive-Project/TheHive/issues/131)
- Custom fields for case template [\#12](https://github.com/TheHive-Project/TheHive/issues/12)

**Fixed bugs:**

- A locked user can use the API to create / delete / list cases \(and more\) [\#251](https://github.com/TheHive-Project/TheHive/issues/251)
- Fix case metrics malformed definitions [\#248](https://github.com/TheHive-Project/TheHive/issues/248)
- Sorting alerts by severity fails [\#242](https://github.com/TheHive-Project/TheHive/issues/242)
- Alerting Panel: Typo Correction [\#240](https://github.com/TheHive-Project/TheHive/issues/240)
- files in alerts are limited to 32kB [\#237](https://github.com/TheHive-Project/TheHive/issues/237)
- Alert can contain inconsistent data [\#234](https://github.com/TheHive-Project/TheHive/issues/234)
- Search do not work with non-latin characters [\#223](https://github.com/TheHive-Project/TheHive/issues/223)
- report status not updated after finish [\#212](https://github.com/TheHive-Project/TheHive/issues/212)

## [2.11.3](https://github.com/TheHive-Project/TheHive/tree/2.11.3) (2017-06-14)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/debian/2.11.2...2.11.3)

**Fixed bugs:**

- Unable to add tasks to case template [\#239](https://github.com/TheHive-Project/TheHive/issues/239)
- Problem Start TheHive on Ubuntu 16.04 [\#238](https://github.com/TheHive-Project/TheHive/issues/238)
- MISP synchronization doesn't retrieve all events [\#236](https://github.com/TheHive-Project/TheHive/issues/236)

## [2.11.2](https://github.com/TheHive-Project/TheHive/tree/2.11.2) (2017-05-24)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.11.1...2.11.2)

**Implemented enhancements:**

- Visually distinguish between analyzed and non analyzer observables [\#224](https://github.com/TheHive-Project/TheHive/issues/224)
- Add Description Field to Alert Preview Modal [\#218](https://github.com/TheHive-Project/TheHive/issues/218)
- Show case severity in lists [\#188](https://github.com/TheHive-Project/TheHive/issues/188)

**Fixed bugs:**

- MISP synchronization - attributes are not retrieve [\#221](https://github.com/TheHive-Project/TheHive/issues/221)
- MISP synchronization - Alerts are wrongly updated [\#220](https://github.com/TheHive-Project/TheHive/issues/220)
- Cortex jobs from thehive fail silently [\#219](https://github.com/TheHive-Project/TheHive/issues/219)

**Merged pull requests:**

- Fixing links to docu repo [\#213](https://github.com/TheHive-Project/TheHive/pull/213) ([SHSauler](https://github.com/SHSauler))

## [2.11.1](https://github.com/TheHive-Project/TheHive/tree/2.11.1) (2017-05-17)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.11.0...2.11.1)

**Implemented enhancements:**

- Show available reports number for each observable [\#211](https://github.com/TheHive-Project/TheHive/issues/211)
- Merge Duplicate Tasks during Case Merge [\#180](https://github.com/TheHive-Project/TheHive/issues/180)

**Fixed bugs:**

- Case templates not applied when converting an alert to a case [\#206](https://github.com/TheHive-Project/TheHive/issues/206)
- Observable of merged cased might have duplicate tags [\#205](https://github.com/TheHive-Project/TheHive/issues/205)
- Error updating case templates [\#204](https://github.com/TheHive-Project/TheHive/issues/204)

## [2.11.0](https://github.com/TheHive-Project/TheHive/tree/2.11.0) (2017-05-14)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.10.2...2.11.0)

**Implemented enhancements:**

- Display the logos of the integrated external services [\#198](https://github.com/TheHive-Project/TheHive/issues/198)
- TheHive send to many information to Cortex when an analyze is requested [\#196](https://github.com/TheHive-Project/TheHive/issues/196)
- Sort the list of report templates [\#195](https://github.com/TheHive-Project/TheHive/issues/195)
- Add support to .deb and .rpm package generation [\#193](https://github.com/TheHive-Project/TheHive/issues/193)
- Cannot distinguish which analysers run on which cortex instance [\#179](https://github.com/TheHive-Project/TheHive/issues/179)
- Connect to Cortex protected by Basic Auth [\#173](https://github.com/TheHive-Project/TheHive/issues/173)
- Implement the alerting framework feature [\#170](https://github.com/TheHive-Project/TheHive/issues/170)
- Make the flow collapsible, in case details page [\#167](https://github.com/TheHive-Project/TheHive/issues/167)
- Update the datalist filter previews to display meaningful values [\#166](https://github.com/TheHive-Project/TheHive/issues/166)
- Show severity on the "Cases Page" [\#165](https://github.com/TheHive-Project/TheHive/issues/165)
- Add pagination component at the top of all the data lists [\#151](https://github.com/TheHive-Project/TheHive/issues/151)
- Connect to Cortex instance via proxy [\#147](https://github.com/TheHive-Project/TheHive/issues/147)
- Disable field autocomplete on the login form [\#146](https://github.com/TheHive-Project/TheHive/issues/146)
- Refresh the UI's skin [\#145](https://github.com/TheHive-Project/TheHive/issues/145)
- Add support of case template in back-end API [\#144](https://github.com/TheHive-Project/TheHive/issues/144)
- Proxy authentication [\#143](https://github.com/TheHive-Project/TheHive/issues/143)
- Improve logs browsing [\#128](https://github.com/TheHive-Project/TheHive/issues/128)
- Improve logs browsing [\#128](https://github.com/TheHive-Project/TheHive/issues/128)
- Feature request: Autocomplete tags [\#119](https://github.com/TheHive-Project/TheHive/issues/119)
- Ignored MISP events are no longer visible and cannot be imported [\#107](https://github.com/TheHive-Project/TheHive/issues/107)
- MISP import filter / filtering of events [\#86](https://github.com/TheHive-Project/TheHive/issues/86)
- Reordering Tasks [\#21](https://github.com/TheHive-Project/TheHive/issues/21)

**Fixed bugs:**

- Authentication fails with wrong message if database migration is needed [\#200](https://github.com/TheHive-Project/TheHive/issues/200)
- Fix the success message when running a set of analyzers [\#199](https://github.com/TheHive-Project/TheHive/issues/199)
- Duplicate HTTP calls in case page [\#187](https://github.com/TheHive-Project/TheHive/issues/187)
- Job status refresh [\#171](https://github.com/TheHive-Project/TheHive/issues/171)

**Closed issues:**

- Support for cuckoo malware analysis plattform \(link analysis\) [\#181](https://github.com/TheHive-Project/TheHive/issues/181)
- Scala code cleanup [\#153](https://github.com/TheHive-Project/TheHive/issues/153)

**Merged pull requests:**

- Fixed minor typo in template creation and update notifications. [\#194](https://github.com/TheHive-Project/TheHive/pull/194) ([dewoodruff](https://github.com/dewoodruff))

## [2.10.2](https://github.com/TheHive-Project/TheHive/tree/2.10.2) (2017-04-19)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.10.1...2.10.2)

**Implemented enhancements:**

- Run all analyzers on multiple observables from observables view [\#174](https://github.com/TheHive-Project/TheHive/issues/174)
- Add CSRF protection [\#158](https://github.com/TheHive-Project/TheHive/issues/158)
- Persistence for task viewing options [\#157](https://github.com/TheHive-Project/TheHive/issues/157)

**Fixed bugs:**

- MISP import fails [\#169](https://github.com/TheHive-Project/TheHive/issues/169)
- Unauthenticated access to some pages doesn't redirect to login page [\#161](https://github.com/TheHive-Project/TheHive/issues/161)
- Disable readonly access to admin pages, for users without 'admin' role [\#160](https://github.com/TheHive-Project/TheHive/issues/160)
- Secure the usage of angular-ui-notification library [\#159](https://github.com/TheHive-Project/TheHive/issues/159)
- Pagination does not work with 100 results per page [\#152](https://github.com/TheHive-Project/TheHive/issues/152)

**Closed issues:**

- Observable Tags not displayed in 2.10.1 [\#155](https://github.com/TheHive-Project/TheHive/issues/155)

## [2.10.1](https://github.com/TheHive-Project/TheHive/tree/2.10.1) (2017-03-08)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.10.0...2.10.1)

**Implemented enhancements:**

- Feature Request: Ansible build scripts [\#124](https://github.com/TheHive-Project/TheHive/issues/124)
- Remove the "Run all analyzers" option from observables list [\#141](https://github.com/TheHive-Project/TheHive/issues/141)
- Remove duplicate stream callbacks registration [\#138](https://github.com/TheHive-Project/TheHive/issues/138)
- Typo in quick filters [\#134](https://github.com/TheHive-Project/TheHive/issues/134)
- Display a warning when trying to merge an already merged case [\#129](https://github.com/TheHive-Project/TheHive/issues/129)
- Restyle avatar's upload button [\#126](https://github.com/TheHive-Project/TheHive/issues/126)
- Add pagination component at the top of the task log [\#116](https://github.com/TheHive-Project/TheHive/issues/116)
- Disable buttons in MISP event's preview dialog [\#115](https://github.com/TheHive-Project/TheHive/issues/115)
- Make The Hive working on any URL path and not only / [\#114](https://github.com/TheHive-Project/TheHive/issues/114)
- Misleading MISP Event Date and Time [\#101](https://github.com/TheHive-Project/TheHive/issues/101)
- Upgrade to the last version of UI-Bootstrap UI library [\#79](https://github.com/TheHive-Project/TheHive/issues/79)

**Fixed bugs:**

- Fix OTXQuery report template [\#142](https://github.com/TheHive-Project/TheHive/issues/142)
- 401 HTTP responses don't trigger redirection to login page [\#140](https://github.com/TheHive-Project/TheHive/issues/140)
- Fix a JS issue related to inactivity dialog [\#139](https://github.com/TheHive-Project/TheHive/issues/139)
- Flow is not shown [\#127](https://github.com/TheHive-Project/TheHive/issues/127)
- Case merge does not close tasks in merged cases [\#118](https://github.com/TheHive-Project/TheHive/issues/118)
- Web UI doesn't refresh once a report template is deleted [\#113](https://github.com/TheHive-Project/TheHive/issues/113)
- Open log in new windows [\#108](https://github.com/TheHive-Project/TheHive/issues/108)
- Cannot add an observable which datatype has been added by an admin [\#106](https://github.com/TheHive-Project/TheHive/issues/106)
- Observables password hint does not reflect backend change [\#83](https://github.com/TheHive-Project/TheHive/issues/83)

## [2.10.0](https://github.com/TheHive-Project/TheHive/tree/2.10.0) (2017-02-01)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.9.2...2.10.0)

**Implemented enhancements:**

- Improve cases listing page [\#76](https://github.com/TheHive-Project/TheHive/issues/76)
- Feature Request - Add Case Statistics by Severity [\#70](https://github.com/TheHive-Project/TheHive/issues/70)
- Use avatars in user profiles [\#69](https://github.com/TheHive-Project/TheHive/issues/69)
- Allow \(un\)set observable as IOC from the observable's page [\#68](https://github.com/TheHive-Project/TheHive/issues/68)
- When closing a task, close the associated tab as well [\#66](https://github.com/TheHive-Project/TheHive/issues/66)
- Load the Current Cases View when Closing a Case [\#61](https://github.com/TheHive-Project/TheHive/issues/61)
- Externalize observable analysis [\#53](https://github.com/TheHive-Project/TheHive/issues/53)
- Changeable case owner [\#30](https://github.com/TheHive-Project/TheHive/issues/30)
- Make release process easier [\#28](https://github.com/TheHive-Project/TheHive/issues/28)
- Newly created case template not visible in NEW case until logout/login [\#26](https://github.com/TheHive-Project/TheHive/issues/26)

**Fixed bugs:**

- Template Limit Bug [\#105](https://github.com/TheHive-Project/TheHive/issues/105)
- Bug related case [\#97](https://github.com/TheHive-Project/TheHive/issues/97)
- Case TLP should be set to AMBER by default [\#96](https://github.com/TheHive-Project/TheHive/issues/96)
- User is not notified on MISP error [\#88](https://github.com/TheHive-Project/TheHive/issues/88)
- Locked users cannot be assignee of cases [\#77](https://github.com/TheHive-Project/TheHive/issues/77)
- Task descriptions from case templates are not applied [\#65](https://github.com/TheHive-Project/TheHive/issues/65)
- Add an already exist observable returns an unexpected error [\#63](https://github.com/TheHive-Project/TheHive/issues/63)
- Don't use deleted obserables to link cases [\#62](https://github.com/TheHive-Project/TheHive/issues/62)
- Assign a default role to new users and remove the ability to assign empty roles [\#60](https://github.com/TheHive-Project/TheHive/issues/60)
- Locked users are still able to log in [\#59](https://github.com/TheHive-Project/TheHive/issues/59)
- MISP events counter is not refreshed [\#58](https://github.com/TheHive-Project/TheHive/issues/58)
- Make sure to clear new task log editor [\#57](https://github.com/TheHive-Project/TheHive/issues/57)
- Missing markdown editor in case close dialog [\#42](https://github.com/TheHive-Project/TheHive/issues/42)

**Closed issues:**

- Database schema update \(v8\) [\#67](https://github.com/TheHive-Project/TheHive/issues/67)
- Add support for more filetypes to PE\_info analyser [\#54](https://github.com/TheHive-Project/TheHive/issues/54)
- Create an analyzer to get information about PE file [\#51](https://github.com/TheHive-Project/TheHive/issues/51)
- PhishTank Analyzer [\#40](https://github.com/TheHive-Project/TheHive/issues/40)
- OTX Analyzer [\#32](https://github.com/TheHive-Project/TheHive/issues/32)

**Merged pull requests:**

- AlienVault OTX Analyzer [\#39](https://github.com/TheHive-Project/TheHive/pull/39) ([ecapuano](https://github.com/ecapuano))

## [2.9.2](https://github.com/TheHive-Project/TheHive/tree/2.9.2) (2017-01-19)
[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/2.9.1...2.9.2)

**Implemented enhancements:**

- Feature Request - Add observable statistics [\#71](https://github.com/TheHive-Project/TheHive/issues/71)

**Fixed bugs:**

- docker image: $.post\(...\).success is not a function [\#95](https://github.com/TheHive-Project/TheHive/issues/95)

## [2.9.1](https://github.com/TheHive-Project/TheHive/tree/2.9.1) (2016-11-28)
**Implemented enhancements:**

- Statistics on a per case template name / prefix basis [\#31](https://github.com/TheHive-Project/TheHive/issues/31)
- Observable Viewing Page [\#17](https://github.com/TheHive-Project/TheHive/issues/17)
- Update logo and favicon [\#45](https://github.com/TheHive-Project/TheHive/issues/45)
- Inconsistent wording between the login and user management pages [\#44](https://github.com/TheHive-Project/TheHive/issues/44)
- MaxMind Analyzer 'Short Report' has hard-coded language [\#23](https://github.com/TheHive-Project/TheHive/issues/23)
- Don't update imported case from MISP if it is deleted or merged [\#22](https://github.com/TheHive-Project/TheHive/issues/22)
- Case merging [\#14](https://github.com/TheHive-Project/TheHive/issues/14)
- New analyzer to check URL categories [\#24](https://github.com/TheHive-Project/TheHive/pull/24) ([ecapuano](https://github.com/ecapuano))

**Fixed bugs:**

- Resource not found by Assets controller [\#38](https://github.com/TheHive-Project/TheHive/issues/38)
- NPE occurs at startup if conf directory doesn't exists [\#41](https://github.com/TheHive-Project/TheHive/issues/41)
- Systemd startup script does not work [\#29](https://github.com/TheHive-Project/TheHive/issues/29)
- MISP event parsing error when it doesn't contain any attribute [\#25](https://github.com/TheHive-Project/TheHive/issues/25)
- Phantom tabs [\#18](https://github.com/TheHive-Project/TheHive/issues/18)
- The Action button of observables list is blank [\#15](https://github.com/TheHive-Project/TheHive/issues/15)
- Description becomes empty when you cancel an edition [\#13](https://github.com/TheHive-Project/TheHive/issues/13)
- Metric Labels Not Showing in Case View [\#10](https://github.com/TheHive-Project/TheHive/issues/10)
- chrome on os x - header alignment [\#5](https://github.com/TheHive-Project/TheHive/issues/5)
- Tags not saving when creating observable. [\#4](https://github.com/TheHive-Project/TheHive/issues/4)

**Closed issues:**

- Statistics based on Tags [\#37](https://github.com/TheHive-Project/TheHive/issues/37)
- Give us something to work with! [\#2](https://github.com/TheHive-Project/TheHive/issues/2)

**Merged pull requests:**

- Fix "Run from Docker" [\#9](https://github.com/TheHive-Project/TheHive/pull/9) ([2xyo](https://github.com/2xyo))
- Fixing a Simple Typo [\#6](https://github.com/TheHive-Project/TheHive/pull/6) ([swannysec](https://github.com/swannysec))
- Fixed broken link to Wiki [\#1](https://github.com/TheHive-Project/TheHive/pull/1) ([Neo23x0](https://github.com/Neo23x0))



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*