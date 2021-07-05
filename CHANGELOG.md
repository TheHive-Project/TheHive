# Change Log

## [4.1.7](https://github.com/TheHive-Project/TheHive/milestone/76) (2021-07-05)

**Implemented enhancements:**

- [Enhancement] Copy the database even if the schema version doesn't match (with force flag) [\#2105](https://github.com/TheHive-Project/TheHive/issues/2105)

**Fixed bugs:**

- [Bug] Issue with Migration 3.5.1 -> 4.1.6 [\#2089](https://github.com/TheHive-Project/TheHive/issues/2089)
- [Bug] Fix serialization for case number messages [\#2107](https://github.com/TheHive-Project/TheHive/issues/2107)

## [4.1.6](https://github.com/TheHive-Project/TheHive/milestone/75) (2021-06-14)

**Implemented enhancements:**

- [Feature Request] Add API to repair database [\#2081](https://github.com/TheHive-Project/TheHive/issues/2081)

**Fixed bugs:**

- [Bug] Editing case template tasks incorrectly removes tasks  [\#1926](https://github.com/TheHive-Project/TheHive/issues/1926)
- [Bug] When creating cases from alerts via API, the same case number gets assigned to multiple distinct cases [\#1970](https://github.com/TheHive-Project/TheHive/issues/1970)
- [Bug] src:MISP-ORG missing on MISP alerts [\#2058](https://github.com/TheHive-Project/TheHive/issues/2058)
- [Bug] Analyzer reports dissapear in 4.1.5 (observable already exists error) [\#2059](https://github.com/TheHive-Project/TheHive/issues/2059)
- [Bug] CaseNumber Conflict [\#2061](https://github.com/TheHive-Project/TheHive/issues/2061)
- [Bug] Alert tags glitch after previewing alert [\#2062](https://github.com/TheHive-Project/TheHive/issues/2062)
- [Bug] Case Template content mixed across organisations [\#2068](https://github.com/TheHive-Project/TheHive/issues/2068)
- [Bug] /api/v1/<user.ID>/key returns 401/403 if user hasn't key [\#2069](https://github.com/TheHive-Project/TheHive/issues/2069)
- [Bug] When API call returns failure, actual response depends on  authentication methods [\#2070](https://github.com/TheHive-Project/TheHive/issues/2070)
- [Bug] Deleting observables doesn't produce audit log [\#2076](https://github.com/TheHive-Project/TheHive/issues/2076)

## [4.1.5](https://github.com/TheHive-Project/TheHive/milestone/74) (2021-06-03)

**Implemented enhancements:**

- [Enhancement] Improve API v1 [\#2010](https://github.com/TheHive-Project/TheHive/issues/2010)
- [Enhancement] Improve integrity checks [\#2033](https://github.com/TheHive-Project/TheHive/issues/2033)
- [Feature Request] Add the ability to copy data from a database to another [\#2042](https://github.com/TheHive-Project/TheHive/issues/2042)
- [Feature Request] Add organisation name in responder data [\#2048](https://github.com/TheHive-Project/TheHive/issues/2048)
- [Feature Request] Add alert updatable fields [\#2055](https://github.com/TheHive-Project/TheHive/issues/2055)

**Closed issues:**

- [Bug] API GET /api/alert fails when similarity is specified [\#1981](https://github.com/TheHive-Project/TheHive/issues/1981)

**Fixed bugs:**

- [Bug] Imported filter does not show alerts which are associated to removed cases [\#1940](https://github.com/TheHive-Project/TheHive/issues/1940)
- [Bug]  Observable already exists [\#1963](https://github.com/TheHive-Project/TheHive/issues/1963)
- [Bug] using quick-filter "shared with my org" takes arround 90 seconds [\#1979](https://github.com/TheHive-Project/TheHive/issues/1979)
- [Bug] Analyzer reports dissapear in 4.1.4 (observable already exists error) [\#1982](https://github.com/TheHive-Project/TheHive/issues/1982)
- [Bug] Merge Into Case search by title not working [\#1983](https://github.com/TheHive-Project/TheHive/issues/1983)
- [Bug] Able to merge alert into closed case, even though it is not allowed [\#1985](https://github.com/TheHive-Project/TheHive/issues/1985)
- Custom Dashboards issue - see #1877 as reference [Bug] [\#2001](https://github.com/TheHive-Project/TheHive/issues/2001)
- [Question] A security issue? [\#2008](https://github.com/TheHive-Project/TheHive/issues/2008)
- [Bug] Case description edit button misplaced after description update [\#2012](https://github.com/TheHive-Project/TheHive/issues/2012)
- [Bug] Analyzer list is not refreshing properly when organization change [\#2025](https://github.com/TheHive-Project/TheHive/issues/2025)
- [Bug] Alert list constantly resets [\#2030](https://github.com/TheHive-Project/TheHive/issues/2030)
- [Bug] Can't Delete Case Custom Field (contains [ character) After Migration [\#2043](https://github.com/TheHive-Project/TheHive/issues/2043)
- [Bug] Unable to find case by Case Number [\#2044](https://github.com/TheHive-Project/TheHive/issues/2044)
- [Bug] add TTP error message on the hive - 4.1.4-1 [\#2045](https://github.com/TheHive-Project/TheHive/issues/2045)
- [Bug] Merge Into Case search by title not a real search [\#2049](https://github.com/TheHive-Project/TheHive/issues/2049)
- [Bug] max-attributes param not working for MISP [\#2050](https://github.com/TheHive-Project/TheHive/issues/2050)
- [Bug] Invalid output when a file observable already exist [\#2054](https://github.com/TheHive-Project/TheHive/issues/2054)

## [4.1.4](https://github.com/TheHive-Project/TheHive/milestone/73) (2021-04-15)

**Implemented enhancements:**

- [Feature Request] Sort case templates in alert Import drop down menu [\#1956](https://github.com/TheHive-Project/TheHive/issues/1956)
- [Enhancement] Make alert deletion more robust [\#1967](https://github.com/TheHive-Project/TheHive/issues/1967)

**Fixed bugs:**

- [Bug] Alert counter refresh not working [\#1911](https://github.com/TheHive-Project/TheHive/issues/1911)
- [Bug] Enabled or Disabled Taxonomies doesn't work [\#1957](https://github.com/TheHive-Project/TheHive/issues/1957)
- [Bug] TheHive 4.1.3-1 Task in Case is not visible [\#1964](https://github.com/TheHive-Project/TheHive/issues/1964)
- [Bug] Aggregation on custom fields provides incorect result (again) [\#1965](https://github.com/TheHive-Project/TheHive/issues/1965)
- [Bug] TheHive startup times out if schema evolution is long [\#1966](https://github.com/TheHive-Project/TheHive/issues/1966)
- [Bug] Default analyst rol cant add TTPs [\#1968](https://github.com/TheHive-Project/TheHive/issues/1968)
- [Bug] Links section should not be empty in Observables details view [\#1972](https://github.com/TheHive-Project/TheHive/issues/1972)
- [Bug] Deleting a shared rule case for org2 - deleting observables (sharing to org2) from the org1. [\#1973](https://github.com/TheHive-Project/TheHive/issues/1973)
- [Bug] AddTagToArtifact operation not working in 4.1.3 [\#1974](https://github.com/TheHive-Project/TheHive/issues/1974)

## [4.1.3](https://github.com/TheHive-Project/TheHive/milestone/72) (2021-04-12)

**Implemented enhancements:**

- [Improvement] Cleanup deprecated filter usage [\#1922](https://github.com/TheHive-Project/TheHive/issues/1922)
- [Improvement] Make the property "Imported" in alerts optimised for index [\#1923](https://github.com/TheHive-Project/TheHive/issues/1923)
- [Feature Request] Display case templates in alphabetic order in "New case" menu [\#1925](https://github.com/TheHive-Project/TheHive/issues/1925)
- [Enhancement] Prevent the application to start if database initialisation fails [\#1935](https://github.com/TheHive-Project/TheHive/issues/1935)
- [Enhancement] Improve performance [\#1946](https://github.com/TheHive-Project/TheHive/issues/1946)
- [Enhancement] Remove blocking queries in some UI pages [\#1948](https://github.com/TheHive-Project/TheHive/issues/1948)
- [Enhancement] Use polluingDuration config from the UI [\#1951](https://github.com/TheHive-Project/TheHive/issues/1951)
- [Enhancement] Disable confirm buttons in import dialogs [\#1953](https://github.com/TheHive-Project/TheHive/issues/1953)
- [Enhancement] Add environment file in service [\#1954](https://github.com/TheHive-Project/TheHive/issues/1954)

**Fixed bugs:**

- [Bug] Add "Not assigned" to Assignee field on task page for tasks without an assigned user [\#1508](https://github.com/TheHive-Project/TheHive/issues/1508)
- [Bug] (Still) slow loading of list-tags endpoint with 4.1.2 [\#1914](https://github.com/TheHive-Project/TheHive/issues/1914)
- [Bug] Aggregation on custom fields provides incorect result [\#1921](https://github.com/TheHive-Project/TheHive/issues/1921)
- [Bug] Very slow load of Case Task list in UI in 4.1.2 [\#1927](https://github.com/TheHive-Project/TheHive/issues/1927)
- [Bug] Task "Take" Button not working [\#1931](https://github.com/TheHive-Project/TheHive/issues/1931)
- [Bug] Cluster: new nodes fail to start when the oldest node has been restarted [\#1934](https://github.com/TheHive-Project/TheHive/issues/1934)
- [Bug] Index status page is very slow [\#1936](https://github.com/TheHive-Project/TheHive/issues/1936)
- [Bug] Update of color in tags [\#1950](https://github.com/TheHive-Project/TheHive/issues/1950)

## [4.1.2](https://github.com/TheHive-Project/TheHive/milestone/71) (2021-03-29)

**Implemented enhancements:**

- [Feature Request] Add case search by TTP [\#1893](https://github.com/TheHive-Project/TheHive/issues/1893)

**Fixed bugs:**

- [Bug] Slow loading of TheHive because of Tags [\#1869](https://github.com/TheHive-Project/TheHive/issues/1869)
- [Bug] After migration from 4.0.5 to 4.1.0 old tasklogs are not returned by "/api/v1/query?name=case-task-logs" query [\#1875](https://github.com/TheHive-Project/TheHive/issues/1875)
- Dashboards - custom fields  [\#1877](https://github.com/TheHive-Project/TheHive/issues/1877)
- [Bug] TH 4.1.1 : Filter by "IMPORTED" does not work for alerts imported into existing cases [\#1891](https://github.com/TheHive-Project/TheHive/issues/1891)
- [Bug] Fix the S3 configuration options [\#1892](https://github.com/TheHive-Project/TheHive/issues/1892)
- [Bug] All attachments in task logs disappeared following upgrade to 4.1.1 [\#1894](https://github.com/TheHive-Project/TheHive/issues/1894)
- [Bug] Continued performance issues after upgrade to 4.1.1 [\#1896](https://github.com/TheHive-Project/TheHive/issues/1896)
- [Bug] Fix issues dashboard list [\#1901](https://github.com/TheHive-Project/TheHive/issues/1901)
- [Bug] Migration tool migrates unsupported elastic index [\#1907](https://github.com/TheHive-Project/TheHive/issues/1907)
- [Bug] Folder permissions are not correctly set in docker image [\#1908](https://github.com/TheHive-Project/TheHive/issues/1908)

## [4.1.1](https://github.com/TheHive-Project/TheHive/milestone/70) (2021-03-23)

**Implemented enhancements:**

- [Feature Request] Include organisation ID in webhooks [\#1865](https://github.com/TheHive-Project/TheHive/issues/1865)

**Closed issues:**

- [Bug] Importing the ATT&CK library fails on 4.1 [\#1862](https://github.com/TheHive-Project/TheHive/issues/1862)
- Thehive4.1.0 Issues with Lucene [\#1863](https://github.com/TheHive-Project/TheHive/issues/1863)

**Fixed bugs:**

- [Bug] TheHive doesn't start if webhook is configured without authentication [\#1859](https://github.com/TheHive-Project/TheHive/issues/1859)
- [Bug] Migration fails from 4.0.5 to 4.1 [\#1861](https://github.com/TheHive-Project/TheHive/issues/1861)
- [Bug] Filter by "IMPORTED" does not work [\#1866](https://github.com/TheHive-Project/TheHive/issues/1866)
- [Bug] TheHive doesn't start in cluster mode (serializer is missing) [\#1868](https://github.com/TheHive-Project/TheHive/issues/1868)
- [Bug] Full-text search is slow [\#1870](https://github.com/TheHive-Project/TheHive/issues/1870)

## [4.1.0](https://github.com/TheHive-Project/TheHive/milestone/56) (2021-03-18)

**Implemented enhancements:**

- Suggestion: Marge cases on the oldest and close the newest as duplicated [\#960](https://github.com/TheHive-Project/TheHive/issues/960)
- [Feature Request] Implement case merging feature [\#1264](https://github.com/TheHive-Project/TheHive/issues/1264)
- [Enhancement] Enrich v1 API [\#1454](https://github.com/TheHive-Project/TheHive/issues/1454)
- [Feature Request] Prompt to save changes to Case Templates before navigating away [\#1524](https://github.com/TheHive-Project/TheHive/issues/1524)
- [Feature Request] allow user to choose the format of the date displayed [\#1583](https://github.com/TheHive-Project/TheHive/issues/1583)
- [Feature Request] Add support to taxonomies [\#1670](https://github.com/TheHive-Project/TheHive/issues/1670)
- [Enhancement] Improve search performance by using external index engine [\#1731](https://github.com/TheHive-Project/TheHive/issues/1731)
- [Feature Request] Default filter of alert case similarity : add "No filter" as an option [\#1750](https://github.com/TheHive-Project/TheHive/issues/1750)
- [Feature Request] Add MITRE ATT&CK support [\#1766](https://github.com/TheHive-Project/TheHive/issues/1766)
- [Feature Request] Show case status in the default view (open / closed as FP / closed as TP, etc.)  [\#1781](https://github.com/TheHive-Project/TheHive/issues/1781)
- [Enhancement] Create logfile after installation [\#1789](https://github.com/TheHive-Project/TheHive/issues/1789)
- [Feature Request] Revamp case template admin section [\#1804](https://github.com/TheHive-Project/TheHive/issues/1804)
- [Feature Request] Improve date fields in data lists [\#1807](https://github.com/TheHive-Project/TheHive/issues/1807)
- [Feature Request] Enhance organisation list page [\#1813](https://github.com/TheHive-Project/TheHive/issues/1813)
- [Feature Request] Add a platform status page [\#1815](https://github.com/TheHive-Project/TheHive/issues/1815)
- [Feature Request] Add organisation free tags administration section [\#1816](https://github.com/TheHive-Project/TheHive/issues/1816)
- [Feature Request] Enhance the dashboard list section [\#1817](https://github.com/TheHive-Project/TheHive/issues/1817)
- [Enhancement] Add migration from TheHive 3.5.1 [\#1818](https://github.com/TheHive-Project/TheHive/issues/1818)
- [Feature Request] Additional case bulk actions [\#1821](https://github.com/TheHive-Project/TheHive/issues/1821)
- [Feature Request] Add support to "isEmpty" filter option [\#1824](https://github.com/TheHive-Project/TheHive/issues/1824)
- [Feature Request] Improve task list page [\#1831](https://github.com/TheHive-Project/TheHive/issues/1831)
- [Feature Request] Disk usage monitoring API route [\#1843](https://github.com/TheHive-Project/TheHive/issues/1843)
- [Feature Request] Allow cancelling task action request [\#1844](https://github.com/TheHive-Project/TheHive/issues/1844)
- [Feature Request] Add more quick filters to case list [\#1848](https://github.com/TheHive-Project/TheHive/issues/1848)
- [Feature Request] Add support of authentication in webhooks [\#1850](https://github.com/TheHive-Project/TheHive/issues/1850)
- [Feature Request] Allow removing a custom field from a case [\#1852](https://github.com/TheHive-Project/TheHive/issues/1852)

**Closed issues:**

- [Feature Request] Alphabetize Case Template view [\#1551](https://github.com/TheHive-Project/TheHive/issues/1551)
- [Feature Request] Add the ability to directly close a task [\#1727](https://github.com/TheHive-Project/TheHive/issues/1727)
- [Question] Tags and custom fields can be seen across organisations / potential for data leakage  [\#1778](https://github.com/TheHive-Project/TheHive/issues/1778)
- [Feature Request] Allow user to reorder case templates, or display them in alphabetic order [\#1787](https://github.com/TheHive-Project/TheHive/issues/1787)
- [Repository] Improve github issue templates [\#1840](https://github.com/TheHive-Project/TheHive/issues/1840)

**Fixed bugs:**

- Can not view or delete alert when delete the case that created by Import Alert [\#1123](https://github.com/TheHive-Project/TheHive/issues/1123)
- Imported Alerts Cannot be Deleted [\#1201](https://github.com/TheHive-Project/TheHive/issues/1201)
- [Bug] Creating Cases via API ignores the owner field [\#1473](https://github.com/TheHive-Project/TheHive/issues/1473)
- [Bug] Missing cases migrating from TH3 to TH4 [\#1682](https://github.com/TheHive-Project/TheHive/issues/1682)
- [Bug] Attachment files are not deleted from local filesystem storage when logs is deleted [\#1687](https://github.com/TheHive-Project/TheHive/issues/1687)
- [Bug] Impossible to switch organization if organization name contains an accent [\#1741](https://github.com/TheHive-Project/TheHive/issues/1741)
- [Bug] Filtering issue [\#1753](https://github.com/TheHive-Project/TheHive/issues/1753)
- Identical URL Observables can still be added multiple times to the same case [\#1756](https://github.com/TheHive-Project/TheHive/issues/1756)
- [Bug] Integrity checks for user deduplication is not run when an user is added [\#1759](https://github.com/TheHive-Project/TheHive/issues/1759)
- [Bug] Deleting a shared case on org2 doesn't delete task from the Org1 resulting in log spam and undeletable task [\#1767](https://github.com/TheHive-Project/TheHive/issues/1767)
- [Bug] Fix pivoting from donuts to search pages on custom fields based widgets [\#1777](https://github.com/TheHive-Project/TheHive/issues/1777)
- [Bug] Unable to migrate to TH 4.0.5 [\#1785](https://github.com/TheHive-Project/TheHive/issues/1785)
- [Bug] Elapsed time for re-opened cases is showed as "closed". [\#1796](https://github.com/TheHive-Project/TheHive/issues/1796)
- [Bug] Observables list doesn't reload  [\#1802](https://github.com/TheHive-Project/TheHive/issues/1802)
- [Bug] Error in handling users included in many organisations [\#1803](https://github.com/TheHive-Project/TheHive/issues/1803)
- [Bug] Organisation users list doesn't include update date [\#1805](https://github.com/TheHive-Project/TheHive/issues/1805)
- [Bug] Reveal API key not working for users with profile analyst [\#1806](https://github.com/TheHive-Project/TheHive/issues/1806)
- [Bug] Observables not present in some events imported from MISP [\#1819](https://github.com/TheHive-Project/TheHive/issues/1819)
- [Bug] Migration: parameter input is unusable [\#1827](https://github.com/TheHive-Project/TheHive/issues/1827)
- [Bug] Migration of caseTemplate without task fails [\#1828](https://github.com/TheHive-Project/TheHive/issues/1828)
- [Bug] - Use API v1 to fetch observable job history [\#1838](https://github.com/TheHive-Project/TheHive/issues/1838)
- [Bug] File observables with special character in name can not be downloaded [\#1842](https://github.com/TheHive-Project/TheHive/issues/1842)
- [Bug] Shared dashboards are not editable [\#1849](https://github.com/TheHive-Project/TheHive/issues/1849)
- [Bug] Disable the Audit search section [\#1851](https://github.com/TheHive-Project/TheHive/issues/1851)

## [4.0.5](https://github.com/TheHive-Project/TheHive/milestone/68) (2021-02-08)

**Implemented enhancements:**

- Support for using asterisks by tag-filtering [\#933](https://github.com/TheHive-Project/TheHive/issues/933)
- "Close tasks and case" deletes tasks instead of closing them [\#1755](https://github.com/TheHive-Project/TheHive/issues/1755)
- [Enhancement] Add schema update status in status API [\#1782](https://github.com/TheHive-Project/TheHive/issues/1782)

**Closed issues:**

- Running TheHive 4.0.1-1 it appears that application.log is no longer rotated. [\#1746](https://github.com/TheHive-Project/TheHive/issues/1746)

**Fixed bugs:**

- [Bug] RPM package does not create secret.conf file [\#1248](https://github.com/TheHive-Project/TheHive/issues/1248)
- [Bug] More webhooks or more detailed webhook events [\#1739](https://github.com/TheHive-Project/TheHive/issues/1739)
- [Bug] Webhooks opening infinite amount of files [\#1743](https://github.com/TheHive-Project/TheHive/issues/1743)
- [Bug] Dashboards are always created as private [\#1754](https://github.com/TheHive-Project/TheHive/issues/1754)
- [Bug]/Unable to get MISP organisation [\#1758](https://github.com/TheHive-Project/TheHive/issues/1758)
- [Bug] TheHive 4 Cluster and Haproxy with roundrobin [\#1760](https://github.com/TheHive-Project/TheHive/issues/1760)
- [Bug] TheHive -> MISP works. MISP -> TheHive not. [\#1761](https://github.com/TheHive-Project/TheHive/issues/1761)
- [Bug] TheHive 4.0.4 cannot show tasks created in previous versions [\#1763](https://github.com/TheHive-Project/TheHive/issues/1763)
- [Bug] `Imported` property in Alerts not taken into account  [\#1769](https://github.com/TheHive-Project/TheHive/issues/1769)
- [Bug] Sort field list in dashboard widget filters [\#1771](https://github.com/TheHive-Project/TheHive/issues/1771)
- [Bug] Dashboard on organisation (and other) doesn't work [\#1772](https://github.com/TheHive-Project/TheHive/issues/1772)
- [BUG] Cannot link multiple organisations together [\#1773](https://github.com/TheHive-Project/TheHive/issues/1773)
- [Bug] Fix custom field filters in v0 APIs [\#1779](https://github.com/TheHive-Project/TheHive/issues/1779)

## [4.0.4](https://github.com/TheHive-Project/TheHive/milestone/67) (2021-01-12)

**Implemented enhancements:**

- [Feature Request] Add alert observable API endpoints [\#1732](https://github.com/TheHive-Project/TheHive/issues/1732)
- [Feature Request] Add alert import date property [\#1733](https://github.com/TheHive-Project/TheHive/issues/1733)
- [Feature Request] Add handling duration properties to imported Alert type [\#1734](https://github.com/TheHive-Project/TheHive/issues/1734)

**Fixed bugs:**

- [Bug] TheHive doesn't start if cassandra is not ready [\#1725](https://github.com/TheHive-Project/TheHive/issues/1725)
- [Bug] Alert imported multiple times (bis) [\#1738](https://github.com/TheHive-Project/TheHive/issues/1738)
- [Bug] Cosmetic fix in alert observables list [\#1744](https://github.com/TheHive-Project/TheHive/issues/1744)

## [4.0.3](https://github.com/TheHive-Project/TheHive/milestone/66) (2020-12-22)

**Implemented enhancements:**

- Providing output details for Responders [\#1293](https://github.com/TheHive-Project/TheHive/issues/1293)
- [Enhancement] Change artifacts by observables on the onMouseOver tooltip of the eye icon of observable [\#1695](https://github.com/TheHive-Project/TheHive/issues/1695)
- [Enhancement] Enhance support of S3 for attachment storage [\#1705](https://github.com/TheHive-Project/TheHive/issues/1705)
- [Enhancement] Update the headers of basic info sections [\#1710](https://github.com/TheHive-Project/TheHive/issues/1710)
- [Enhancement] Add poll duration config for UI Stream [\#1720](https://github.com/TheHive-Project/TheHive/issues/1720)

**Fixed bugs:**

- [Bug] MISP filters are not correctly implemented [\#1685](https://github.com/TheHive-Project/TheHive/issues/1685)
- [Bug] The query "getObservable" doesn't work for alert observables [\#1691](https://github.com/TheHive-Project/TheHive/issues/1691)
- [Bug] Click analyzers mini-report does not load the full report [\#1694](https://github.com/TheHive-Project/TheHive/issues/1694)
- [Bug] Import file observable in gui generate error [\#1697](https://github.com/TheHive-Project/TheHive/issues/1697)
- [Bug] Cannot search for alerts per observables [\#1707](https://github.com/TheHive-Project/TheHive/issues/1707)
- [Bug] Serialization problem in cluster mode [\#1708](https://github.com/TheHive-Project/TheHive/issues/1708)
- [Bug] Issue with sorting [\#1716](https://github.com/TheHive-Project/TheHive/issues/1716)
- [Bug] Identical URL Observables can be added multiple times to the same case [\#1718](https://github.com/TheHive-Project/TheHive/issues/1718)

## [4.0.2](https://github.com/TheHive-Project/TheHive/milestone/64) (2020-11-20)

**Implemented enhancements:**

- [Feature Request] Add a dedicated permission to give access to TheHiveFS [\#1655](https://github.com/TheHive-Project/TheHive/issues/1655)
- [Feature Request] Normalize editable input fields [\#1669](https://github.com/TheHive-Project/TheHive/issues/1669)

**Fixed bugs:**

- [Bug] Unable to list Cases [\#1598](https://github.com/TheHive-Project/TheHive/issues/1598)
- [Bug] Alert to case merge is broken in v4.0.1 [\#1648](https://github.com/TheHive-Project/TheHive/issues/1648)
- [Bug] Attachment.* filters are broken under observable search in v4.0.1  [\#1649](https://github.com/TheHive-Project/TheHive/issues/1649)
- [Bug] Result of observable update API v0 is empty [\#1652](https://github.com/TheHive-Project/TheHive/issues/1652)
- [Bug] Display issue of custom fields [\#1653](https://github.com/TheHive-Project/TheHive/issues/1653)
- [Bug] Persistent AuditSrv:undefined error on 4.0.1 [\#1656](https://github.com/TheHive-Project/TheHive/issues/1656)
- [Bug] Issues with case attachments section [\#1657](https://github.com/TheHive-Project/TheHive/issues/1657)
- [Bug] API method broken: /api/case/artifact/_search in 4.0.1 [\#1659](https://github.com/TheHive-Project/TheHive/issues/1659)
- [Bug] API method broken: /api/case/task/log/_search in 4.0.1 [\#1660](https://github.com/TheHive-Project/TheHive/issues/1660)
- [Bug] Unable to define ES index on migration [\#1661](https://github.com/TheHive-Project/TheHive/issues/1661)
- [Bug] Dashboard max aggregation on custom-integer field does not work [\#1662](https://github.com/TheHive-Project/TheHive/issues/1662)
- [Bug] Missing the fix for errorMessage [\#1666](https://github.com/TheHive-Project/TheHive/issues/1666)
- [Bug] Fix alert details dialog [\#1672](https://github.com/TheHive-Project/TheHive/issues/1672)
- [Bug] error 500 with adding an empty file in Observables of an Alert [\#1673](https://github.com/TheHive-Project/TheHive/issues/1673)
- [Bug] Fix migration of audit logs [\#1676](https://github.com/TheHive-Project/TheHive/issues/1676)

## [4.0.1](https://github.com/TheHive-Project/TheHive/milestone/60) (2020-11-13)

**Implemented enhancements:**

- [Enhancement] Remove gremlin-scala library  [\#1501](https://github.com/TheHive-Project/TheHive/issues/1501)
- [Feature request] Improve case similarity details in alert preview pane [\#1579](https://github.com/TheHive-Project/TheHive/issues/1579)
- [Enhancement] Check tag autocompletion [\#1611](https://github.com/TheHive-Project/TheHive/issues/1611)
- [Feature] Add Cortex related notifiers in notification system [\#1619](https://github.com/TheHive-Project/TheHive/issues/1619)
- [Feature] Add properties related to share [\#1621](https://github.com/TheHive-Project/TheHive/issues/1621)
- [Feature Request] Update user settings view to give access to API key  [\#1623](https://github.com/TheHive-Project/TheHive/issues/1623)
- [Feature Request] Permit to disable similarity (case and alert) for some observable [\#1625](https://github.com/TheHive-Project/TheHive/issues/1625)
- [Enhancement] Add link to report template archive [\#1627](https://github.com/TheHive-Project/TheHive/issues/1627)
- [Enahancement] Display TheHive version in the login page [\#1629](https://github.com/TheHive-Project/TheHive/issues/1629)
- [Feature Request] Display custom fields in alert and case list [\#1637](https://github.com/TheHive-Project/TheHive/issues/1637)
- [Feature Request] Revamp the statistics section in lists [\#1641](https://github.com/TheHive-Project/TheHive/issues/1641)
- [Enhancement] Improve the filter observables panel [\#1642](https://github.com/TheHive-Project/TheHive/issues/1642)
- [Enhancement] Refine the migration of users with admin role [\#1645](https://github.com/TheHive-Project/TheHive/issues/1645)

**Closed issues:**

- [Bug] default MISP connector import line has a typo [\#1595](https://github.com/TheHive-Project/TheHive/issues/1595)

**Fixed bugs:**

- [Bug] MISP->THEHIVE4 'ExportOnly' and 'Exceptions' ignored in application.conf file [\#1482](https://github.com/TheHive-Project/TheHive/issues/1482)
- [Bug] Mobile-responsive Hamburger not visible [\#1290](https://github.com/TheHive-Project/TheHive/issues/1290)
- [Bug] Unable to start TheHive after migration [\#1450](https://github.com/TheHive-Project/TheHive/issues/1450)
- [Bug] Expired session should show a dialog or login page on pageload [\#1456](https://github.com/TheHive-Project/TheHive/issues/1456)
- [Bug] TheHive 4 - Application.conf file  [\#1461](https://github.com/TheHive-Project/TheHive/issues/1461)
- [Bug] Improve migration [\#1469](https://github.com/TheHive-Project/TheHive/issues/1469)
- [Bug] Merge Alert in similar Case button does not work [\#1470](https://github.com/TheHive-Project/TheHive/issues/1470)
- [Bug] Missing Case number in Alert Preview / Similar Cases tab [\#1471](https://github.com/TheHive-Project/TheHive/issues/1471)
- [Bug] Dashboard shared/private [\#1474](https://github.com/TheHive-Project/TheHive/issues/1474)
- [Bug]Migration tool date/number/duration params don't work [\#1478](https://github.com/TheHive-Project/TheHive/issues/1478)
- [Bug] AuditSrv: undefined on non-case page(s), thehive4-4.0.0-1, Ubuntu [\#1479](https://github.com/TheHive-Project/TheHive/issues/1479)
- [Bug] Unable to enumerate tasks via API [\#1483](https://github.com/TheHive-Project/TheHive/issues/1483)
- [Bug] Case close notification displays "#undefined" instead of case number [\#1488](https://github.com/TheHive-Project/TheHive/issues/1488)
- [Bug] Task under "Waiting tasks" and "My tasks" do not display the case number [\#1489](https://github.com/TheHive-Project/TheHive/issues/1489)
- [Bug] Live Stream log in main page is not limited to 10 entries [\#1490](https://github.com/TheHive-Project/TheHive/issues/1490)
- [Bug] Several API Endpoints could never get called due to the routing structure [\#1492](https://github.com/TheHive-Project/TheHive/issues/1492)
- [Bug] Missing link to linked cases from observable details view [\#1494](https://github.com/TheHive-Project/TheHive/issues/1494)
- [Bug] TheHive V4 API Errors "Operation Not Permitted" and "Date format" [\#1496](https://github.com/TheHive-Project/TheHive/issues/1496)
- [Bug] V4 Merge observable tags with existing observables during importing alerts into case [\#1499](https://github.com/TheHive-Project/TheHive/issues/1499)
- [Bug] Multiline dashboard doesn't work [\#1503](https://github.com/TheHive-Project/TheHive/issues/1503)
- [Bug] Tags of observables in Alerts are not created when promoted [\#1510](https://github.com/TheHive-Project/TheHive/issues/1510)
- [Bug] Alert creation fails if alert contains similar observables [\#1514](https://github.com/TheHive-Project/TheHive/issues/1514)
- [Bug] "Undefined" in notification message when a case is closed [\#1515](https://github.com/TheHive-Project/TheHive/issues/1515)
- [Bug] The creation of multiline observable is not possible [\#1517](https://github.com/TheHive-Project/TheHive/issues/1517)
- [Bug] Entrypoint: Waiting for cassandra with --no-config [\#1519](https://github.com/TheHive-Project/TheHive/issues/1519)
- [Bug] Suppress Reduntant AuthenticationFailed Error+Warn [\#1523](https://github.com/TheHive-Project/TheHive/issues/1523)
- [Bug] API v0: "startDate" sort criteria not implemented [\#1540](https://github.com/TheHive-Project/TheHive/issues/1540)
- [Bug] Fix case search in case merge dialog [\#1541](https://github.com/TheHive-Project/TheHive/issues/1541)
- [Bug] Soft-Deleted cases show up as "(Closed at  as  )" in the case list. [\#1543](https://github.com/TheHive-Project/TheHive/issues/1543)
- [Bug] Related cases show only one observable [\#1544](https://github.com/TheHive-Project/TheHive/issues/1544)
- [Bug] An user can create a task even if it doesn't the permission [\#1545](https://github.com/TheHive-Project/TheHive/issues/1545)
- [Bug] Wrong stats url on user and audit [\#1546](https://github.com/TheHive-Project/TheHive/issues/1546)
- [Bug] Add DATETIME information to each task log [\#1547](https://github.com/TheHive-Project/TheHive/issues/1547)
- [Bug] Custom configuration is not correctly read in docker image [\#1548](https://github.com/TheHive-Project/TheHive/issues/1548)
- [Bug] Typo in MFA onboarding [\#1549](https://github.com/TheHive-Project/TheHive/issues/1549)
- [Bug] New custom fields doesn't appear in search criteria [\#1550](https://github.com/TheHive-Project/TheHive/issues/1550)
- [Bug] Custom Field Order ignored [\#1552](https://github.com/TheHive-Project/TheHive/issues/1552)
- [Bug] Additional Fields are discarded during merge [\#1553](https://github.com/TheHive-Project/TheHive/issues/1553)
- [Bug] Unable to list alerts in case's related alerts section [\#1554](https://github.com/TheHive-Project/TheHive/issues/1554)
- [Bug] Deleting the first case breaks the the audit flow until the next restart [\#1556](https://github.com/TheHive-Project/TheHive/issues/1556)
- [Bug] Issues surrounding Alerts merging [\#1557](https://github.com/TheHive-Project/TheHive/issues/1557)
- [Bug] Uncaught exception with duplicate mail type observables when added to case [\#1561](https://github.com/TheHive-Project/TheHive/issues/1561)
- [Bug] Case Tasks get deleted if not started [\#1565](https://github.com/TheHive-Project/TheHive/issues/1565)
- [Bug] Can't export Case tags to MISP event [\#1566](https://github.com/TheHive-Project/TheHive/issues/1566)
- [Bug]The link to similar observable in observable details page doesn't work [\#1567](https://github.com/TheHive-Project/TheHive/issues/1567)
- [Bug] TheHive4 'follow/unfollow' API doesn't return alert objects like TheHive3 does [\#1571](https://github.com/TheHive-Project/TheHive/issues/1571)
- [Bug] Alert Custom Field with integer value [\#1588](https://github.com/TheHive-Project/TheHive/issues/1588)
- [Bug] Tag filter is broken [\#1590](https://github.com/TheHive-Project/TheHive/issues/1590)
- [Bug] Admin user does not have the right to list users of other organisations [\#1592](https://github.com/TheHive-Project/TheHive/issues/1592)
- [Bug] Add missing query operations [\#1599](https://github.com/TheHive-Project/TheHive/issues/1599)
- [Bug] Fix configuration sample [\#1600](https://github.com/TheHive-Project/TheHive/issues/1600)
- [Bug] Analyzer tags are removes if Cortex job fails [\#1610](https://github.com/TheHive-Project/TheHive/issues/1610)
- [Bug] deleted Tasks displayed in MyTasks [\#1612](https://github.com/TheHive-Project/TheHive/issues/1612)
- [Bug] the "_in" query operator doesn't work [\#1617](https://github.com/TheHive-Project/TheHive/issues/1617)
- [Bug] Sort filter field dropdowns [\#1630](https://github.com/TheHive-Project/TheHive/issues/1630)
- [Bug] Alert imported multiple times [\#1631](https://github.com/TheHive-Project/TheHive/issues/1631)
- [Bug] Import observables from analyzer report is broken [\#1633](https://github.com/TheHive-Project/TheHive/issues/1633)
- [Bug] Import observable from a zip archive doesn't work [\#1634](https://github.com/TheHive-Project/TheHive/issues/1634)
- [Bug] Case handling duration attributes are not working in time based dashboard widgets [\#1635](https://github.com/TheHive-Project/TheHive/issues/1635)
- [Bug] Fix custom field in filter forms [\#1636](https://github.com/TheHive-Project/TheHive/issues/1636)
- [Bug] It is possible to add an identical file observable several times in a case [\#1643](https://github.com/TheHive-Project/TheHive/issues/1643)
- [Bug] Hash observables are not correctly export to MISP [\#1644](https://github.com/TheHive-Project/TheHive/issues/1644)

## [4.0.0](https://github.com/TheHive-Project/TheHive/milestone/59) (2020-07-24)

**Implemented enhancements:**

- No longer possible to force usage of a case template (ui setting is missing) [\#1239](https://github.com/TheHive-Project/TheHive/issues/1239)
- Make user management list paginable and sortable with default sort of username [\#1332](https://github.com/TheHive-Project/TheHive/issues/1332)
- Cursor is set wrong on new-Case -> severity [\#1373](https://github.com/TheHive-Project/TheHive/issues/1373)
- [Enhancement] Prevent link with "admin" organisation [\#1395](https://github.com/TheHive-Project/TheHive/issues/1395)
- [Enhancement] An user should not be able to lock himself [\#1396](https://github.com/TheHive-Project/TheHive/issues/1396)
- Performance - Don't load stats if not displayed [\#1401](https://github.com/TheHive-Project/TheHive/issues/1401)
- [RBAC] Add routes guard configuration to secure routes [\#1403](https://github.com/TheHive-Project/TheHive/issues/1403)
- [Enhancement] Add checks for database integrity [\#1404](https://github.com/TheHive-Project/TheHive/issues/1404)
- Use Query APIs in list pages [\#1410](https://github.com/TheHive-Project/TheHive/issues/1410)
- Improve autocomplete queries for tags [\#1411](https://github.com/TheHive-Project/TheHive/issues/1411)
- [Enhancement] Add ability to add tasks in case creation API [\#1414](https://github.com/TheHive-Project/TheHive/issues/1414)
- Improve user details caching [\#1418](https://github.com/TheHive-Project/TheHive/issues/1418)
- Add bulk edit in cases list [\#1423](https://github.com/TheHive-Project/TheHive/issues/1423)
- Use a responder selector window instead of dynamic dropdown menues [\#1431](https://github.com/TheHive-Project/TheHive/issues/1431)
- Show sharing summary in task and observable lists [\#1437](https://github.com/TheHive-Project/TheHive/issues/1437)
- Add some quick filters in tasks list [\#1438](https://github.com/TheHive-Project/TheHive/issues/1438)
- Use assignable users API to populate assignee options [\#1444](https://github.com/TheHive-Project/TheHive/issues/1444)
- Migrate the stats widgets on listing pages [\#1446](https://github.com/TheHive-Project/TheHive/issues/1446)

**Closed issues:**

- Default Dashboards are missing [\#1240](https://github.com/TheHive-Project/TheHive/issues/1240)

**Fixed bugs:**

- [Bug] Migration issues from ES to Cassandra [\#1340](https://github.com/TheHive-Project/TheHive/issues/1340)
- [Bug] Deleting and observable doesn't refresh the list [\#1355](https://github.com/TheHive-Project/TheHive/issues/1355)
- [Bug] Limiting admin rights breaks front end [\#1368](https://github.com/TheHive-Project/TheHive/issues/1368)
- [Bug] Imported Dashboards from TH3 doesn't work [\#1371](https://github.com/TheHive-Project/TheHive/issues/1371)
- [Bug] Top 5 tags in Case -> Stats aren't correctly ordered [\#1372](https://github.com/TheHive-Project/TheHive/issues/1372)
- [Bug] Migration of usernames from ES to Cassandra [\#1374](https://github.com/TheHive-Project/TheHive/issues/1374)
- [Bug] Switching User Organisation failes using header variable authentication [\#1375](https://github.com/TheHive-Project/TheHive/issues/1375)
- [Bug] Tags gets wrong renamed [\#1376](https://github.com/TheHive-Project/TheHive/issues/1376)
- [Bug] MISP integration alert link generated incorrectly [\#1378](https://github.com/TheHive-Project/TheHive/issues/1378)
- [Bug] CustomFields does not appear sorted in the case template [\#1383](https://github.com/TheHive-Project/TheHive/issues/1383)
- [Bug] Users in Admin-Org are not allowed to switch to any other org [\#1385](https://github.com/TheHive-Project/TheHive/issues/1385)
- [Bug] Custom Observable Types can be created multiple-times with the same name [\#1387](https://github.com/TheHive-Project/TheHive/issues/1387)
- [Bug] Issues during Migration - Some Observables are missing [\#1388](https://github.com/TheHive-Project/TheHive/issues/1388)
- [Bug] Proxy configuration is not correctly parsed [\#1392](https://github.com/TheHive-Project/TheHive/issues/1392)
- [Bug] Handle 401 on route failure [\#1402](https://github.com/TheHive-Project/TheHive/issues/1402)
- [Bug] Delete case api fails [\#1405](https://github.com/TheHive-Project/TheHive/issues/1405)
- Fix the filter preview deletion button [\#1412](https://github.com/TheHive-Project/TheHive/issues/1412)
- Fix OAuth redirect handling from Javascript [\#1420](https://github.com/TheHive-Project/TheHive/issues/1420)
- [Bug] Error when exporting a case with severity Critical in MISP [\#1424](https://github.com/TheHive-Project/TheHive/issues/1424)
- [Bug] Cases owned by non-linked organisations visible to all organisations, potential data leakage [\#1427](https://github.com/TheHive-Project/TheHive/issues/1427)
- [Bug] TheHive doesn't start correctly [\#1429](https://github.com/TheHive-Project/TheHive/issues/1429)
- [Bug] Permission is not correctly checked for MISP export [\#1432](https://github.com/TheHive-Project/TheHive/issues/1432)
- Observable type deletion doesn't wait for the confirmation [\#1433](https://github.com/TheHive-Project/TheHive/issues/1433)
- Fix rendering of jobs in search section [\#1434](https://github.com/TheHive-Project/TheHive/issues/1434)
- Remove obsolete options in Search page [\#1436](https://github.com/TheHive-Project/TheHive/issues/1436)
- [Bug] Click on dashboards to access filtered data [\#1445](https://github.com/TheHive-Project/TheHive/issues/1445)
- [Bug] Pivoting from dashboard to search page is loosing the date filter [\#1448](https://github.com/TheHive-Project/TheHive/issues/1448)
- [Bug] Series' filters in dashboard widgets are taken into account [\#1449](https://github.com/TheHive-Project/TheHive/issues/1449)

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
