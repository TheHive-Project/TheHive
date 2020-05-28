# Change Log

## [4.0.0-RC3](https://github.com/TheHive-Project/TheHive/milestone/58) (2020-05-27)

**Implemented enhancements:**

- [Feature] Show case sharing information on main case overview page [\#1277](https://github.com/TheHive-Project/TheHive/issues/1277)
- [Feature] Allow users to be part of multiple organisations [\#1316](https://github.com/TheHive-Project/TheHive/issues/1316)
- [Enhancement] Hide multifactor option in user-dialog if Enable Multi-Factor Authentication is disabled. [\#1317](https://github.com/TheHive-Project/TheHive/issues/1317)
- [Feature] Authentication API should return user information [\#1346](https://github.com/TheHive-Project/TheHive/issues/1346)
- [Enhancement] Enrich queries [\#1353](https://github.com/TheHive-Project/TheHive/issues/1353)

**Fixed bugs:**

- [Bug] Unable to add new datatypes [\#1288](https://github.com/TheHive-Project/TheHive/issues/1288)
- [Bug] Unable to bulk delete an alert [\#1310](https://github.com/TheHive-Project/TheHive/issues/1310)
- [Bug] importing alert as template not working [\#1311](https://github.com/TheHive-Project/TheHive/issues/1311)
- [Bug] Tasks not displayed when importing alert into case with case template [\#1312](https://github.com/TheHive-Project/TheHive/issues/1312)
- [Bug] WebHook creation does not work [\#1318](https://github.com/TheHive-Project/TheHive/issues/1318)
- [Bug] Opening Analyzer Templates without Cortex brings error message [\#1319](https://github.com/TheHive-Project/TheHive/issues/1319)
- [Bug] Case Statistics does not correctly display top 5 tags  [\#1320](https://github.com/TheHive-Project/TheHive/issues/1320)
- [Bug] Importing of some user failes [\#1323](https://github.com/TheHive-Project/TheHive/issues/1323)
- [Bug] invisible dashborards [\#1324](https://github.com/TheHive-Project/TheHive/issues/1324)
- [Bug] Assignee List in Case and Tasks is no longer sorted Alphabetical [\#1327](https://github.com/TheHive-Project/TheHive/issues/1327)
- [Bug] Sorting in Observables of a case does not work [\#1328](https://github.com/TheHive-Project/TheHive/issues/1328)
- [Bug] Read-only has options to edit task-logs [\#1334](https://github.com/TheHive-Project/TheHive/issues/1334)
- [Bug] Adding a custom-field on an open case requires a reload, otherwise field is not visible [\#1336](https://github.com/TheHive-Project/TheHive/issues/1336)
- [Bug] severity change when create new case don't work [\#1338](https://github.com/TheHive-Project/TheHive/issues/1338)
- [Bug] Migration issues from ES to Cassandra [\#1340](https://github.com/TheHive-Project/TheHive/issues/1340)
- [Bug] The filter operator "_child" is missing [\#1344](https://github.com/TheHive-Project/TheHive/issues/1344)
- [Bug] Webhook compatibility issues on custom-fields [\#1345](https://github.com/TheHive-Project/TheHive/issues/1345)
- [Bug] Object sent to responder doesn't contain parent [\#1348](https://github.com/TheHive-Project/TheHive/issues/1348)
- [Bug] Show Sharing link to all users [\#1351](https://github.com/TheHive-Project/TheHive/issues/1351)
- [Bug] Unable to create case or alert using integer custom field [\#1356](https://github.com/TheHive-Project/TheHive/issues/1356)
- [Bug] Get observables of a case using API not working [\#1357](https://github.com/TheHive-Project/TheHive/issues/1357)
- [Bug] OAuth2 authentication doesn't redirect to home page on success [\#1360](https://github.com/TheHive-Project/TheHive/issues/1360)
- [Bug] Confusion on same alert on different organisations [\#1361](https://github.com/TheHive-Project/TheHive/issues/1361)
- [Bug] Search link to observable does not work [\#1365](https://github.com/TheHive-Project/TheHive/issues/1365)
- [Bug] Unable to vienw analysis report from observable list [\#1366](https://github.com/TheHive-Project/TheHive/issues/1366)
- [Bug] MISP export succeeds but show an error message [\#1367](https://github.com/TheHive-Project/TheHive/issues/1367)
- [Bug] rc3 migration script failure [\#1369](https://github.com/TheHive-Project/TheHive/issues/1369)
- [Bug] set HTTP redirect correctly when behind a reverse proxy  [\#1370](https://github.com/TheHive-Project/TheHive/issues/1370)

## [4.0.0-RC2](https://github.com/TheHive-Project/TheHive/milestone/54) (2020-05-07)

**Implemented enhancements:**

- Custom severity levels for alerts and cases [\#363](https://github.com/TheHive-Project/TheHive/issues/363)
- A (received) Shared Case is displayed as sender/owner [\#1245](https://github.com/TheHive-Project/TheHive/issues/1245)
- FR: Alignment of case custom-fields (metrics) [\#1246](https://github.com/TheHive-Project/TheHive/issues/1246)
- Add information about the age of a Case  [\#1257](https://github.com/TheHive-Project/TheHive/issues/1257)
- Providing output details for Responders [\#1293](https://github.com/TheHive-Project/TheHive/issues/1293)
- Add support to multi-factor authentication [\#1303](https://github.com/TheHive-Project/TheHive/issues/1303)
- Add support to webhooks [\#1306](https://github.com/TheHive-Project/TheHive/issues/1306)

**Closed issues:**

- [Bug] Attachment stored in thehive but not in configured file-storage [\#1244](https://github.com/TheHive-Project/TheHive/issues/1244)

**Fixed bugs:**

- [Bug] TH doesn't find cases related to an alert's artifacts [\#1236](https://github.com/TheHive-Project/TheHive/issues/1236)
- [Bug] Creation of multiple user with same login within same org [\#1237](https://github.com/TheHive-Project/TheHive/issues/1237)
- Date is now a required attribute for generating an Alert [\#1238](https://github.com/TheHive-Project/TheHive/issues/1238)
- [Bug] Case Template default values can't be set during template creation [\#1241](https://github.com/TheHive-Project/TheHive/issues/1241)
- SearchSrv.NotFoundError  [\#1242](https://github.com/TheHive-Project/TheHive/issues/1242)
- Assignee is not changeable [\#1243](https://github.com/TheHive-Project/TheHive/issues/1243)
- [Bug] In TheHive, a user is a member of one or more organisations. One user has a profile for each organisation and can have different profiles for different organisations.  [\#1247](https://github.com/TheHive-Project/TheHive/issues/1247)
- [Bug] RPM package does not create secret.conf file [\#1248](https://github.com/TheHive-Project/TheHive/issues/1248)
- [Bug] Unable to save new or imported dashboards in 4.0-RC1 [\#1250](https://github.com/TheHive-Project/TheHive/issues/1250)
- [Bug] Header Variable authentication does not work [\#1251](https://github.com/TheHive-Project/TheHive/issues/1251)
- Filtering by custom fields returns no results [\#1252](https://github.com/TheHive-Project/TheHive/issues/1252)
- Cannot Deleted user - Error "OrgUserCtrl: org.thp.thehive.models.User not found" [\#1253](https://github.com/TheHive-Project/TheHive/issues/1253)
- [Bug] Error while importing Alert in TH4 [\#1255](https://github.com/TheHive-Project/TheHive/issues/1255)
- [Bug] Cortex errors [\#1270](https://github.com/TheHive-Project/TheHive/issues/1270)
- [Bug] error when closing a reopened case [\#1271](https://github.com/TheHive-Project/TheHive/issues/1271)
- [Bug] Unable to rename/update case template Name field [\#1275](https://github.com/TheHive-Project/TheHive/issues/1275)
- [Bug] Wrong dataType sent to Cortex (responders) [\#1279](https://github.com/TheHive-Project/TheHive/issues/1279)
- [Bug] Changing task name removes other tasks [\#1281](https://github.com/TheHive-Project/TheHive/issues/1281)
- [Bug] Disable deleting a share with owner = true [\#1283](https://github.com/TheHive-Project/TheHive/issues/1283)
- [Bug] Responder actions not displayed in Case, Task and Observable pages [\#1300](https://github.com/TheHive-Project/TheHive/issues/1300)
- [Bug] Custom field should be readonly [\#1307](https://github.com/TheHive-Project/TheHive/issues/1307)
- [Bug] Unable to display long analyzer report from observables list [\#1309](https://github.com/TheHive-Project/TheHive/issues/1309)
