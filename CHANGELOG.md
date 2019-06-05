# Change Log

## [3.4.0-4C1](https://github.com/TheHive-Project/TheHive/tree/HEAD) (2019-06-05)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.3.1...3.4.0-4C1)

**Implemented enhancements:**

- Allow to import file from Cortex report [\#982](https://github.com/TheHive-Project/TheHive/issues/982)
- Remove metrics module [\#975](https://github.com/TheHive-Project/TheHive/issues/975)
- Upgrade frontend libraries [\#966](https://github.com/TheHive-Project/TheHive/issues/966)
- Cortex AddArtifactToCase AssignCase [\#922](https://github.com/TheHive-Project/TheHive/issues/922)
- Communication to ElasticSearch via HTTP API 9200 [\#913](https://github.com/TheHive-Project/TheHive/issues/913)
- Add Cortex AssignCase [\#924](https://github.com/TheHive-Project/TheHive/pull/924) ([zpriddy](https://github.com/zpriddy))
- Support Elasticsearch 6.x clusters [\#623](https://github.com/TheHive-Project/TheHive/issues/623)

**Fixed bugs:**

- Donut dashboard metric values are not transformed to searches [\#972](https://github.com/TheHive-Project/TheHive/issues/972)
- Bulk merge of alerts does not merge the tags [\#994](https://github.com/TheHive-Project/TheHive/issues/994)
- Java 11 build crash [\#990](https://github.com/TheHive-Project/TheHive/issues/990)
- Failure to load datatypes [\#988](https://github.com/TheHive-Project/TheHive/issues/988)
- Fix search page base filter [\#983](https://github.com/TheHive-Project/TheHive/issues/983)
- Authentication Error when using Hive API \(Patch\) [\#951](https://github.com/TheHive-Project/TheHive/issues/951)

**Closed issues:**

- bintray repo for deb packages not signed [\#976](https://github.com/TheHive-Project/TheHive/issues/976)
- Set alert to status "Ignored" via API does not work [\#955](https://github.com/TheHive-Project/TheHive/issues/955)

**Merged pull requests:**

- Scalligraph [\#980](https://github.com/TheHive-Project/TheHive/pull/980) ([BillOTei](https://github.com/BillOTei))
- Add 'My open cases' and 'New & Updated alerts' to quick filters [\#925](https://github.com/TheHive-Project/TheHive/pull/925) ([zpriddy](https://github.com/zpriddy))

## [3.3.1](https://github.com/TheHive-Project/TheHive/tree/3.3.1) (2019-05-22)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.3.0...3.3.1)

**Fixed bugs:**

- THP-SEC-ADV-2017-001: Privilege Escalation in all Versions of TheHive [\#408](https://github.com/TheHive-Project/TheHive/issues/408)

## [3.3.0](https://github.com/TheHive-Project/TheHive/tree/3.3.0) (2019-03-19)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.3.0-RC6...3.3.0)

**Fixed bugs:**

- Merge case by CaseID Broken [\#930](https://github.com/TheHive-Project/TheHive/issues/930)

## [3.3.0-RC6](https://github.com/TheHive-Project/TheHive/tree/3.3.0-RC6) (2019-03-07)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.3.0-RC5...3.3.0-RC6)

**Implemented enhancements:**

- Add Tags to an Alert with Responder [\#912](https://github.com/TheHive-Project/TheHive/issues/912)
- Dashboards - Add text widget [\#908](https://github.com/TheHive-Project/TheHive/issues/908)
- Empty case still available when disabled [\#901](https://github.com/TheHive-Project/TheHive/issues/901)
- Support for filtering Tags by prefix \(using asterisk, % or something\) in search dialog [\#666](https://github.com/TheHive-Project/TheHive/issues/666)

**Closed issues:**

- Dynamic \(auto-refresh\) of cases is break in 3.3.0-RC5 [\#907](https://github.com/TheHive-Project/TheHive/issues/907)
- Hostname Artifact [\#900](https://github.com/TheHive-Project/TheHive/issues/900)
- DOS issue: Firefox crashing TheHive [\#899](https://github.com/TheHive-Project/TheHive/issues/899)

## [3.3.0-RC5](https://github.com/TheHive-Project/TheHive/tree/3.3.0-RC5) (2019-02-23)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.3.0-RC4...3.3.0-RC5)

**Implemented enhancements:**

- Mouseover text for alert preview [\#897](https://github.com/TheHive-Project/TheHive/issues/897)

**Fixed bugs:**

- dashboard clicks are not correctly translated to tag filters [\#896](https://github.com/TheHive-Project/TheHive/issues/896)
- Search results not visible [\#895](https://github.com/TheHive-Project/TheHive/issues/895)

## [3.3.0-RC4](https://github.com/TheHive-Project/TheHive/tree/3.3.0-RC4) (2019-02-22)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.3.0-RC3...3.3.0-RC4)

**Implemented enhancements:**

- Use empty case modal when merging alerts and no templates are defined [\#893](https://github.com/TheHive-Project/TheHive/issues/893)

**Fixed bugs:**

- Issue with navigation from dashboard clickable donuts to search page [\#894](https://github.com/TheHive-Project/TheHive/issues/894)
- Hide Empty Case Button Broken [\#890](https://github.com/TheHive-Project/TheHive/issues/890)

## [3.3.0-RC3](https://github.com/TheHive-Project/TheHive/tree/3.3.0-RC3) (2019-02-21)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.3.0-RC2...3.3.0-RC3)

**Implemented enhancements:**

- Add a UI configuration admin section [\#888](https://github.com/TheHive-Project/TheHive/issues/888)
- Add a Related Alerts link to case details view [\#884](https://github.com/TheHive-Project/TheHive/issues/884)
- Update Copyright with year 2019 [\#879](https://github.com/TheHive-Project/TheHive/issues/879)
- Provide a quick link to copy alert id [\#870](https://github.com/TheHive-Project/TheHive/issues/870)
- \[BUG\] Audit trail for alert ignore [\#863](https://github.com/TheHive-Project/TheHive/issues/863)
- Related artifacts: IOC/not IOC [\#838](https://github.com/TheHive-Project/TheHive/issues/838)
- Feature: Add "auto-completion" to the UI [\#831](https://github.com/TheHive-Project/TheHive/issues/831)
- Improvement: Upload of observables seem to fail "silently" [\#829](https://github.com/TheHive-Project/TheHive/issues/829)
- Feature Request: link to and from Hive to MISP [\#820](https://github.com/TheHive-Project/TheHive/issues/820)
- Disable clickable widgets in dashboard edit mode [\#485](https://github.com/TheHive-Project/TheHive/issues/485)
- Ability to disable "New Case" -\> "Empty case" [\#449](https://github.com/TheHive-Project/TheHive/issues/449)

**Fixed bugs:**

- Drone build fails on pull-requests [\#882](https://github.com/TheHive-Project/TheHive/issues/882)
- AKKA version missmatch [\#877](https://github.com/TheHive-Project/TheHive/issues/877)
- Label Typo in Updated Alerts [\#874](https://github.com/TheHive-Project/TheHive/issues/874)
- Log message related to MISP synchronization is confusing [\#871](https://github.com/TheHive-Project/TheHive/issues/871)
- Cortex responders with DataType `thehive:case\_artifact` do not show up within thehive when attempting to run them for observables. [\#869](https://github.com/TheHive-Project/TheHive/issues/869)
- Alert updates and tracking \(follow\) [\#856](https://github.com/TheHive-Project/TheHive/issues/856)

**Merged pull requests:**

- Update akka version [\#878](https://github.com/TheHive-Project/TheHive/pull/878) ([zpriddy](https://github.com/zpriddy))
- Fix Update Label to Warning [\#873](https://github.com/TheHive-Project/TheHive/pull/873) ([zpriddy](https://github.com/zpriddy))

## [3.3.0-RC2](https://github.com/TheHive-Project/TheHive/tree/3.3.0-RC2) (2019-02-07)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.3.0-RC1...3.3.0-RC2)

**Fixed bugs:**

- Java dependency of DEB package is broken [\#867](https://github.com/TheHive-Project/TheHive/issues/867)

## [3.3.0-RC1](https://github.com/TheHive-Project/TheHive/tree/3.3.0-RC1) (2019-02-06)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.2.1...3.3.0-RC1)

**Implemented enhancements:**

- \[BUG\] Session cookie received with API token [\#864](https://github.com/TheHive-Project/TheHive/issues/864)
- Add support to Java versions, higher than 8 [\#861](https://github.com/TheHive-Project/TheHive/issues/861)
- MISP - Add an Event Tag instead of/additionnally to Attribute Tag [\#836](https://github.com/TheHive-Project/TheHive/issues/836)
- sorting in alerts [\#824](https://github.com/TheHive-Project/TheHive/issues/824)
- Improve case template selection for case creation [\#769](https://github.com/TheHive-Project/TheHive/issues/769)
- Bulk Merge Alerts into Case [\#271](https://github.com/TheHive-Project/TheHive/issues/271)
- Merge alerts directly to a case [\#826](https://github.com/TheHive-Project/TheHive/issues/826)
- Tag normalization [\#657](https://github.com/TheHive-Project/TheHive/pull/657) ([Viltaria](https://github.com/Viltaria))

**Fixed bugs:**

- Alert updates and tracking \(follow\) [\#856](https://github.com/TheHive-Project/TheHive/issues/856)
- Assigned Tasks do not show up in 'My Tasks' before they are started [\#845](https://github.com/TheHive-Project/TheHive/issues/845)
- Delete user from Thehive: DELETE /api/user/user1 returned 500 org.elastic4play.InternalError: user can't be removed [\#844](https://github.com/TheHive-Project/TheHive/issues/844)

## [3.2.1](https://github.com/TheHive-Project/TheHive/tree/3.2.1) (2018-12-20)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.2.0...3.2.1)

**Fixed bugs:**

- Bug UI "Tooltip" / Hint is cropped by window borders [\#832](https://github.com/TheHive-Project/TheHive/issues/832)
- Can't unset case template when alert is imported [\#825](https://github.com/TheHive-Project/TheHive/issues/825)
- Potential Regression: Case templates cannot be exported in 3.2.0 [\#823](https://github.com/TheHive-Project/TheHive/issues/823)
- Tag order is reversed if a case is created from an alert [\#810](https://github.com/TheHive-Project/TheHive/issues/810)

**Merged pull requests:**

- Make improvements to configuration file [\#828](https://github.com/TheHive-Project/TheHive/pull/828) ([adl1995](https://github.com/adl1995))

## [3.2.0](https://github.com/TheHive-Project/TheHive/tree/3.2.0) (2018-11-29)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.2.0-RC1...3.2.0)

**Implemented enhancements:**

- Add configuration for drone continuous integration [\#803](https://github.com/TheHive-Project/TheHive/issues/803)

**Fixed bugs:**

- Error when uploading password protected zips as observables [\#805](https://github.com/TheHive-Project/TheHive/issues/805)
- Lowercase user ID coming from HTTP header [\#808](https://github.com/TheHive-Project/TheHive/issues/808)
- Error when uploading password protected zips as observables [\#805](https://github.com/TheHive-Project/TheHive/issues/805)

## [3.2.0-RC1](https://github.com/TheHive-Project/TheHive/tree/3.2.0-RC1) (2018-11-16)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.1.2...3.2.0-RC1)

**Implemented enhancements:**

- Add ability to add a log in responder operation [\#795](https://github.com/TheHive-Project/TheHive/issues/795)
- Add responder actions in dashboard [\#794](https://github.com/TheHive-Project/TheHive/issues/794)
- Show observable description on mouseover observables [\#793](https://github.com/TheHive-Project/TheHive/issues/793)
- Update Play [\#791](https://github.com/TheHive-Project/TheHive/issues/791)
- Show tags of observables in Alert preview [\#778](https://github.com/TheHive-Project/TheHive/issues/778)
- Observable Value gets cleared when changing its type \(importing it from an analyser result\) [\#763](https://github.com/TheHive-Project/TheHive/issues/763)
- Add confirmation dialogs when running a responder [\#762](https://github.com/TheHive-Project/TheHive/issues/762)
- Support header variable authentication [\#554](https://github.com/TheHive-Project/TheHive/issues/554)
- Whitelist of tags for MISP alerts [\#481](https://github.com/TheHive-Project/TheHive/issues/481)

**Fixed bugs:**

- MISP synchronization fails if event contains attachment with invalid name [\#801](https://github.com/TheHive-Project/TheHive/issues/801)
- Observable creation doesn't allow multiline observables [\#790](https://github.com/TheHive-Project/TheHive/issues/790)
- A user with "write" permission can delete a case using API [\#773](https://github.com/TheHive-Project/TheHive/issues/773)
- Basic authentication method should be disabled by default [\#772](https://github.com/TheHive-Project/TheHive/issues/772)
- Case search from dashboard clic "invalid filters error" [\#761](https://github.com/TheHive-Project/TheHive/issues/761)
- Intermittently losing Cortex [\#739](https://github.com/TheHive-Project/TheHive/issues/739)

**Merged pull requests:**

- Added Integration with FireEye iSIGHT [\#755](https://github.com/TheHive-Project/TheHive/pull/755) ([garanews](https://github.com/garanews))

## [3.1.2](https://github.com/TheHive-Project/TheHive/tree/3.1.2) (2018-10-12)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.1.1...3.1.2)

**Fixed bugs:**

- Cortex polling settings break startup [\#754](https://github.com/TheHive-Project/TheHive/issues/754)

## [3.1.1](https://github.com/TheHive-Project/TheHive/tree/3.1.1) (2018-10-09)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.1.0...3.1.1)

**Implemented enhancements:**

- Allow TheHive to use a custom root context [\#752](https://github.com/TheHive-Project/TheHive/issues/752)
- Change Debian dependencies [\#751](https://github.com/TheHive-Project/TheHive/issues/751)
- Publish stable versions in beta package channels [\#733](https://github.com/TheHive-Project/TheHive/issues/733)
- url category to MISP: poll for default [\#732](https://github.com/TheHive-Project/TheHive/issues/732)

**Fixed bugs:**

- Console output should not be logged in syslog [\#749](https://github.com/TheHive-Project/TheHive/issues/749)
- Update breaks RHEL [\#743](https://github.com/TheHive-Project/TheHive/issues/743)
- Observable Result Icons Not Displaying [\#738](https://github.com/TheHive-Project/TheHive/issues/738)
- UPN attribute is not correctly lowercased [\#736](https://github.com/TheHive-Project/TheHive/issues/736)

**Closed issues:**

- Artifact tags are overwritten by alert sourceRef during import to case [\#734](https://github.com/TheHive-Project/TheHive/issues/734)

## [3.1.0](https://github.com/TheHive-Project/TheHive/tree/3.1.0) (2018-09-25)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.1.0-RC3...3.1.0)

**Implemented enhancements:**

- Add MarkAlertAsRead action to responders [\#729](https://github.com/TheHive-Project/TheHive/issues/729)
- AddCustomField responder operation [\#724](https://github.com/TheHive-Project/TheHive/issues/724)
- 3.1.0RC3: Browsing to negative case ids is possible [\#713](https://github.com/TheHive-Project/TheHive/issues/713)

**Fixed bugs:**

- RPM Updates not available \(404\) [\#719](https://github.com/TheHive-Project/TheHive/issues/719)
- Observables not being displayed [\#655](https://github.com/TheHive-Project/TheHive/issues/655)
- TheHive Hyperlinking [\#723](https://github.com/TheHive-Project/TheHive/issues/723)
- Multiple responder actions does not seem to be handled [\#722](https://github.com/TheHive-Project/TheHive/issues/722)
- API allows alert creation with duplicate artifacts [\#720](https://github.com/TheHive-Project/TheHive/issues/720)
- 3.0.1RC3: certificate based authentication failes as attributes are not correctly lowercased [\#714](https://github.com/TheHive-Project/TheHive/issues/714)
- Fix PAP labels [\#711](https://github.com/TheHive-Project/TheHive/issues/711)

**Closed issues:**

- Cortex Connector [\#721](https://github.com/TheHive-Project/TheHive/issues/721)
- Markdown syntex not rendered correctly [\#718](https://github.com/TheHive-Project/TheHive/issues/718)
- 3.1.0RC3: Search produces errors on screen [\#712](https://github.com/TheHive-Project/TheHive/issues/712)
- TheHive:Alerts don't send observables to Responders [\#725](https://github.com/TheHive-Project/TheHive/issues/725)

**Merged pull requests:**

- CloseTask responder operation [\#728](https://github.com/TheHive-Project/TheHive/pull/728) ([srilumpa](https://github.com/srilumpa))
- Add AddTagToArtifact action to responders [\#717](https://github.com/TheHive-Project/TheHive/pull/717) ([srilumpa](https://github.com/srilumpa))

## [3.1.0-RC3](https://github.com/TheHive-Project/TheHive/tree/3.1.0-RC3) (2018-09-06)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.1.0-RC2...3.1.0-RC3)

**Implemented enhancements:**

- Extend Case Description Field [\#81](https://github.com/TheHive-Project/TheHive/issues/81)
- Display task description via a collapsible row [\#709](https://github.com/TheHive-Project/TheHive/issues/709)
- Allow task group auto complete in case template admin section [\#707](https://github.com/TheHive-Project/TheHive/issues/707)
- Display task group in global task lists [\#705](https://github.com/TheHive-Project/TheHive/issues/705)
- Make task group input optional [\#696](https://github.com/TheHive-Project/TheHive/issues/696)
- Related Cases: See \(x\) more links [\#690](https://github.com/TheHive-Project/TheHive/issues/690)
- Search section: Search for a string over all types of objects [\#689](https://github.com/TheHive-Project/TheHive/issues/689)
- Filter on computedHandlingDuration in SearchDialog fails [\#688](https://github.com/TheHive-Project/TheHive/issues/688)
- Change layout of observable creation form [\#706](https://github.com/TheHive-Project/TheHive/pull/706) ([srilumpa](https://github.com/srilumpa))

**Fixed bugs:**

- Adding new observables to an alert retrospectively is impossible [\#511](https://github.com/TheHive-Project/TheHive/issues/511)
- .sbt build of current git version fails with x-pack-transport error [\#710](https://github.com/TheHive-Project/TheHive/issues/710)
- PKI authentication fails if user name in certificate has the wrong case [\#700](https://github.com/TheHive-Project/TheHive/issues/700)
- Error handling deletion and re creation of file observables [\#699](https://github.com/TheHive-Project/TheHive/issues/699)
- Start waiting tasks when adding task logs [\#695](https://github.com/TheHive-Project/TheHive/issues/695)

## [3.1.0-RC2](https://github.com/TheHive-Project/TheHive/tree/3.1.0-RC2) (2018-08-27)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.1.0-RC1...3.1.0-RC2)

**Implemented enhancements:**

- Add a search box to quickly search for case by caseId [\#685](https://github.com/TheHive-Project/TheHive/issues/685)
- MISP Exports in livestream miss hyperlink to caseid [\#684](https://github.com/TheHive-Project/TheHive/issues/684)
- Remember task list configuration \(grouped/list\) [\#681](https://github.com/TheHive-Project/TheHive/issues/681)
- x509 certificate authentication option 'wantClientAuth' [\#667](https://github.com/TheHive-Project/TheHive/issues/667)
- TheHive 3.1RC1: Slow reaction if Cortex is \(unclear\) unreachable [\#664](https://github.com/TheHive-Project/TheHive/issues/664)
- TheHive 3.1RC1: Add status to cases and tasks in new search page [\#663](https://github.com/TheHive-Project/TheHive/issues/663)
- TheHive 3.1RC1: Add Username that executes an active response to json data field of responder [\#662](https://github.com/TheHive-Project/TheHive/issues/662)
- Ability to set custom fields as mandatory [\#652](https://github.com/TheHive-Project/TheHive/issues/652)
- Application.conf needs clarifications [\#606](https://github.com/TheHive-Project/TheHive/issues/606)
- Observable type boxes doesn't line break on alert preview pane [\#593](https://github.com/TheHive-Project/TheHive/issues/593)
- On branch betterDescriptions [\#660](https://github.com/TheHive-Project/TheHive/pull/660) ([secdecompiled](https://github.com/secdecompiled))

**Fixed bugs:**

- The hive docker image has no latest tag [\#670](https://github.com/TheHive-Project/TheHive/issues/670)
- case metrics unordered in cases [\#419](https://github.com/TheHive-Project/TheHive/issues/419)
- 3.1.0-RC1- Tasks list is limited to 10 items. [\#679](https://github.com/TheHive-Project/TheHive/issues/679)
- WebUI inaccessible after upgrading to 3.1.0-0-RC1 \(elastic4play and Play exceptions\) [\#674](https://github.com/TheHive-Project/TheHive/issues/674)
- play.crypto.secret is depecrated [\#671](https://github.com/TheHive-Project/TheHive/issues/671)
- 'Tagged as' displayed in Related Cases even if cases are untagged [\#594](https://github.com/TheHive-Project/TheHive/issues/594)
- Horizontal Scrolling and Word-Wrap options for Logs [\#573](https://github.com/TheHive-Project/TheHive/issues/573)
- Dashboard visualizations do not work with custom fields [\#478](https://github.com/TheHive-Project/TheHive/issues/478)

**Closed issues:**

- ES Mapping bug [\#680](https://github.com/TheHive-Project/TheHive/issues/680)
- ignore - delete me [\#675](https://github.com/TheHive-Project/TheHive/issues/675)
- HTTPS not working with Keystore [\#669](https://github.com/TheHive-Project/TheHive/issues/669)

**Merged pull requests:**

- Update Cortex reference.conf [\#668](https://github.com/TheHive-Project/TheHive/pull/668) ([ErnHem](https://github.com/ErnHem))
- Fix some minor typos [\#658](https://github.com/TheHive-Project/TheHive/pull/658) ([srilumpa](https://github.com/srilumpa))
- Move input group addons from right to left for better usage [\#672](https://github.com/TheHive-Project/TheHive/pull/672) ([srilumpa](https://github.com/srilumpa))

## [3.1.0-RC1](https://github.com/TheHive-Project/TheHive/tree/3.1.0-RC1) (2018-07-31)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.10...3.1.0-RC1)

**Implemented enhancements:**

- Display drop-down for custom fields sorted alphabetically [\#653](https://github.com/TheHive-Project/TheHive/issues/653)
- Custom fields in Alerts? [\#635](https://github.com/TheHive-Project/TheHive/issues/635)
- Check Cortex authentication in status page [\#625](https://github.com/TheHive-Project/TheHive/issues/625)
- Revamp the search section capabilities [\#620](https://github.com/TheHive-Project/TheHive/issues/620)
- New TheHive-Project repository [\#618](https://github.com/TheHive-Project/TheHive/issues/618)
- Add PAP to case to indicate which kind of action is allowed [\#616](https://github.com/TheHive-Project/TheHive/issues/616)
- Ability to execute active response on any element of TheHive [\#609](https://github.com/TheHive-Project/TheHive/issues/609)
- Consider providing checksums for the release files [\#590](https://github.com/TheHive-Project/TheHive/issues/590)
- Start Task - Button [\#540](https://github.com/TheHive-Project/TheHive/issues/540)
- Handling malware as zip protected file [\#538](https://github.com/TheHive-Project/TheHive/issues/538)
- Auto-refresh for Dashboards [\#476](https://github.com/TheHive-Project/TheHive/issues/476)
- Assign Tasks to users from the Tasks tab [\#426](https://github.com/TheHive-Project/TheHive/issues/426)
- Make The Hive MISP integration sharing vs pull configurable [\#374](https://github.com/TheHive-Project/TheHive/issues/374)
- MISP Sharing Improvements [\#366](https://github.com/TheHive-Project/TheHive/issues/366)
- Output of analyzer as new observable [\#246](https://github.com/TheHive-Project/TheHive/issues/246)
- Ability to have nested tasks [\#148](https://github.com/TheHive-Project/TheHive/issues/148)
- Single-Sign On support [\#354](https://github.com/TheHive-Project/TheHive/issues/354)

**Fixed bugs:**

- Default value of custom fields are not saved [\#649](https://github.com/TheHive-Project/TheHive/issues/649)
- Attachments with character "\#" in the filename are wrongly proceesed [\#645](https://github.com/TheHive-Project/TheHive/issues/645)
- Session does not expire correctly [\#640](https://github.com/TheHive-Project/TheHive/issues/640)
- Dashboards contain analyzer IDs instead of correct names [\#608](https://github.com/TheHive-Project/TheHive/issues/608)
- Error with Single Sign-On on TheHive with X.509 Certificates [\#600](https://github.com/TheHive-Project/TheHive/issues/600)
- Entity case XXXXXXXXXX not found - After deleting case [\#534](https://github.com/TheHive-Project/TheHive/issues/534)
- Artifacts reports are not merged when merging cases [\#446](https://github.com/TheHive-Project/TheHive/issues/446)
- If cortex modules fails in some way, it is permanently repolled by TheHive [\#324](https://github.com/TheHive-Project/TheHive/issues/324)
- Previewing alerts fails with "too many substreams open" due to case similarity process [\#280](https://github.com/TheHive-Project/TheHive/issues/280)

**Closed issues:**

- add double quotes in mini reports [\#634](https://github.com/TheHive-Project/TheHive/issues/634)

**Merged pull requests:**

- fix bug in AlertListCtrl [\#642](https://github.com/TheHive-Project/TheHive/pull/642) ([billmurrin](https://github.com/billmurrin))
- flag for Windows env [\#641](https://github.com/TheHive-Project/TheHive/pull/641) ([billmurrin](https://github.com/billmurrin))
- 426 - assign tasks to users from tasks tab [\#628](https://github.com/TheHive-Project/TheHive/pull/628) ([billmurrin](https://github.com/billmurrin))
- Fix installation links [\#603](https://github.com/TheHive-Project/TheHive/pull/603) ([Viltaria](https://github.com/Viltaria))

## [3.0.10](https://github.com/TheHive-Project/TheHive/tree/3.0.10) (2018-05-29)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.9...3.0.10)

**Implemented enhancements:**

- Rotate logs [\#579](https://github.com/TheHive-Project/TheHive/issues/579)
- Send caseId to Cortex analyzer [\#564](https://github.com/TheHive-Project/TheHive/issues/564)
- Poll for connectors status and display [\#563](https://github.com/TheHive-Project/TheHive/issues/563)
- Sort related cases by related artifacts amount [\#548](https://github.com/TheHive-Project/TheHive/issues/548)
- Time Calculation for individual tasks [\#546](https://github.com/TheHive-Project/TheHive/issues/546)

**Fixed bugs:**

- Wrong error message when creating a observable with invalid data [\#592](https://github.com/TheHive-Project/TheHive/issues/592)
- Analyzer name not reflected in modal view of mini-reports [\#586](https://github.com/TheHive-Project/TheHive/issues/586)
- Invalid searches lead to read error messages [\#584](https://github.com/TheHive-Project/TheHive/issues/584)
- Merge case by ID brings red error message if not a number in textfield [\#583](https://github.com/TheHive-Project/TheHive/issues/583)
- Open cases not listed after deletion of merged case in UI [\#557](https://github.com/TheHive-Project/TheHive/issues/557)
- Making dashboards private makes them "invisible" [\#555](https://github.com/TheHive-Project/TheHive/issues/555)
- MISP Synchronisation error [\#522](https://github.com/TheHive-Project/TheHive/issues/522)
- Short Report is not shown on observables \(3.0.8\) [\#512](https://github.com/TheHive-Project/TheHive/issues/512)

**Closed issues:**

- Max Age Filter Not Working? [\#577](https://github.com/TheHive-Project/TheHive/issues/577)
- Support X-Pack authentication/encryption for elastic [\#570](https://github.com/TheHive-Project/TheHive/issues/570)
- Order the cases list by custom field \[Feature Request\] [\#567](https://github.com/TheHive-Project/TheHive/issues/567)
- Using Postman to test the API, getting "No CSRF token found in headers" [\#549](https://github.com/TheHive-Project/TheHive/issues/549)

## [3.0.9](https://github.com/TheHive-Project/TheHive/tree/3.0.9) (2018-04-13)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.8...3.0.9)

**Fixed bugs:**

- Cortex connection can fail without any error log [\#543](https://github.com/TheHive-Project/TheHive/issues/543)
- PhishTank Cortex Tag is transparent [\#535](https://github.com/TheHive-Project/TheHive/issues/535)
- Naming inconsistencies in Live-Channel [\#531](https://github.com/TheHive-Project/TheHive/issues/531)
- Error when trying to analyze a filename with the Hybrid Analysis analyzer [\#530](https://github.com/TheHive-Project/TheHive/issues/530)
- Long Report isn't shown [\#527](https://github.com/TheHive-Project/TheHive/issues/527)
- Artifacts' sighted flags are not merged when merging cases [\#518](https://github.com/TheHive-Project/TheHive/issues/518)
- TheHive MISP cert validation, the trustAnchors parameter must be non-empty [\#452](https://github.com/TheHive-Project/TheHive/issues/452)

**Closed issues:**

- The Hive - MISP SSL configuration: General SSLEngine problem [\#544](https://github.com/TheHive-Project/TheHive/issues/544)
- Dropdown menu for case templates doesnt have scroll [\#541](https://github.com/TheHive-Project/TheHive/issues/541)

**Merged pull requests:**

- Update spacing for elasticsearch section in docker-compose yml file [\#539](https://github.com/TheHive-Project/TheHive/pull/539) ([jbarlow-mcafee](https://github.com/jbarlow-mcafee))

## [3.0.8](https://github.com/TheHive-Project/TheHive/tree/3.0.8) (2018-04-04)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.7...3.0.8)

**Fixed bugs:**

- Mini reports is not shown when Cortex 2 is used [\#526](https://github.com/TheHive-Project/TheHive/issues/526)
- Session collision when TheHive & Cortex 2 share the same URL [\#525](https://github.com/TheHive-Project/TheHive/issues/525)
- "Run all" in single observable context does not work [\#524](https://github.com/TheHive-Project/TheHive/issues/524)
- Error on displaying analyzers name in report template admin page [\#523](https://github.com/TheHive-Project/TheHive/issues/523)
- Job Analyzer is no longer named in 3.0.7 with Cortex2 [\#521](https://github.com/TheHive-Project/TheHive/issues/521)

**Merged pull requests:**

- Add ElasticSearch file descriptor limit to docker-compose.yml [\#505](https://github.com/TheHive-Project/TheHive/pull/505) ([flmsc](https://github.com/flmsc))

## [3.0.7](https://github.com/TheHive-Project/TheHive/tree/3.0.7) (2018-04-03)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.6...3.0.7)

**Implemented enhancements:**

- Delete Case [\#100](https://github.com/TheHive-Project/TheHive/issues/100)

**Fixed bugs:**

- Display only cortex servers available for each analyzer, in observable details page [\#513](https://github.com/TheHive-Project/TheHive/issues/513)
- Can't save case template in 3.0.6 [\#502](https://github.com/TheHive-Project/TheHive/issues/502)

## [3.0.6](https://github.com/TheHive-Project/TheHive/tree/3.0.6) (2018-03-08)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.5...3.0.6)

**Implemented enhancements:**

- Add compatibility with Cortex 2 [\#466](https://github.com/TheHive-Project/TheHive/issues/466)

**Fixed bugs:**

- Tasks are stripped when merging cases [\#489](https://github.com/TheHive-Project/TheHive/issues/489)

## [3.0.5](https://github.com/TheHive-Project/TheHive/tree/3.0.5) (2018-02-08)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.4...3.0.5)

**Fixed bugs:**

- No reports available for "domain" type [\#469](https://github.com/TheHive-Project/TheHive/issues/469)
- Importing Template Button Non-Functional [\#404](https://github.com/TheHive-Project/TheHive/issues/404)

## [3.0.4](https://github.com/TheHive-Project/TheHive/tree/3.0.4) (2018-02-06)

[Full Changelog](https://github.com/TheHive-Project/TheHive/compare/3.0.3...3.0.4)

**Implemented enhancements:**

- Make alerts searchable through the global search field [\#456](https://github.com/TheHive-Project/TheHive/issues/456)
- Make counts on Counter dashboard's widget clickable [\#455](https://github.com/TheHive-Project/TheHive/issues/455)
- MISP feeds cause the growing of ES audit docs [\#450](https://github.com/TheHive-Project/TheHive/issues/450)
- Case metrics sort [\#418](https://github.com/TheHive-Project/TheHive/issues/418)
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
- Msg_Parser analyser show for all files [\#184](https://github.com/TheHive-Project/TheHive/issues/184)
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
- Show case status and category \(FP, TP, IND\) in related cases [\#229](https://github.com/TheHive-Project/TheHive/issues/229)
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
- Add support for more filetypes to PE_info analyser [\#54](https://github.com/TheHive-Project/TheHive/issues/54)
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

- docker image: \$.post\(...\).success is not a function [\#95](https://github.com/TheHive-Project/TheHive/issues/95)

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

\* _This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)_
