# Change Log

## [2.10.2](https://github.com/CERT-BDF/TheHive/tree/2.10.2) (2017-04-18)
[Full Changelog](https://github.com/CERT-BDF/TheHive/compare/2.10.1...2.10.2)

**Implemented enhancements:**

- Run all analyzers on multiple observables from observables view [\#174](https://github.com/CERT-BDF/TheHive/issues/174)
- Add CSRF protection [\#158](https://github.com/CERT-BDF/TheHive/issues/158)
- Persistence for task viewing options [\#157](https://github.com/CERT-BDF/TheHive/issues/157)

**Fixed bugs:**

- MISP import fails [\#169](https://github.com/CERT-BDF/TheHive/issues/169)
- Unauthenticated access to some pages doesn't redirect to login page [\#161](https://github.com/CERT-BDF/TheHive/issues/161)
- Disable readonly access to admin pages, for users without 'admin' role [\#160](https://github.com/CERT-BDF/TheHive/issues/160)
- Secure the usage of angular-ui-notification library [\#159](https://github.com/CERT-BDF/TheHive/issues/159)
- Pagination does not work with 100 results per page [\#152](https://github.com/CERT-BDF/TheHive/issues/152)

**Closed issues:**

- Observable Tags not displayed in 2.10.1 [\#155](https://github.com/CERT-BDF/TheHive/issues/155)

## [2.10.1](https://github.com/CERT-BDF/TheHive/tree/2.10.1) (2017-03-08)
[Full Changelog](https://github.com/CERT-BDF/TheHive/compare/2.10.0...2.10.1)

**Implemented enhancements:**

- Feature Request: Ansible build scripts [\#124](https://github.com/CERT-BDF/TheHive/issues/124)
- Remove the "Run all analyzers" option from observables list [\#141](https://github.com/CERT-BDF/TheHive/issues/141)
- Remove duplicate stream callbacks registration [\#138](https://github.com/CERT-BDF/TheHive/issues/138)
- Typo in quick filters [\#134](https://github.com/CERT-BDF/TheHive/issues/134)
- Display a warning when trying to merge an already merged case [\#129](https://github.com/CERT-BDF/TheHive/issues/129)
- Restyle avatar's upload button [\#126](https://github.com/CERT-BDF/TheHive/issues/126)
- Add pagination component at the top of the task log [\#116](https://github.com/CERT-BDF/TheHive/issues/116)
- Disable buttons in MISP event's preview dialog [\#115](https://github.com/CERT-BDF/TheHive/issues/115)
- Make The Hive working on any URL path and not only / [\#114](https://github.com/CERT-BDF/TheHive/issues/114)
- Misleading MISP Event Date and Time [\#101](https://github.com/CERT-BDF/TheHive/issues/101)
- Upgrade to the last version of UI-Bootstrap UI library [\#79](https://github.com/CERT-BDF/TheHive/issues/79)

**Fixed bugs:**

- Fix OTXQuery report template [\#142](https://github.com/CERT-BDF/TheHive/issues/142)
- 401 HTTP responses don't trigger redirection to login page [\#140](https://github.com/CERT-BDF/TheHive/issues/140)
- Fix a JS issue related to inactivity dialog [\#139](https://github.com/CERT-BDF/TheHive/issues/139)
- Flow is not shown [\#127](https://github.com/CERT-BDF/TheHive/issues/127)
- Case merge does not close tasks in merged cases [\#118](https://github.com/CERT-BDF/TheHive/issues/118)
- Web UI doesn't refresh once a report template is deleted [\#113](https://github.com/CERT-BDF/TheHive/issues/113)
- Open log in new windows [\#108](https://github.com/CERT-BDF/TheHive/issues/108)
- Cannot add an observable which datatype has been added by an admin [\#106](https://github.com/CERT-BDF/TheHive/issues/106)
- Observables password hint does not reflect backend change [\#83](https://github.com/CERT-BDF/TheHive/issues/83)

## [2.10.0](https://github.com/CERT-BDF/TheHive/tree/2.10.0) (2017-02-01)
[Full Changelog](https://github.com/CERT-BDF/TheHive/compare/2.9.2...2.10.0)

**Implemented enhancements:**

- Improve cases listing page [\#76](https://github.com/CERT-BDF/TheHive/issues/76)
- Feature Request - Add Case Statistics by Severity [\#70](https://github.com/CERT-BDF/TheHive/issues/70)
- Use avatars in user profiles [\#69](https://github.com/CERT-BDF/TheHive/issues/69)
- Allow \(un\)set observable as IOC from the observable's page [\#68](https://github.com/CERT-BDF/TheHive/issues/68)
- When closing a task, close the associated tab as well [\#66](https://github.com/CERT-BDF/TheHive/issues/66)
- Load the Current Cases View when Closing a Case [\#61](https://github.com/CERT-BDF/TheHive/issues/61)
- Externalize observable analysis [\#53](https://github.com/CERT-BDF/TheHive/issues/53)
- Changeable case owner [\#30](https://github.com/CERT-BDF/TheHive/issues/30)
- Make release process easier [\#28](https://github.com/CERT-BDF/TheHive/issues/28)
- Newly created case template not visible in NEW case until logout/login [\#26](https://github.com/CERT-BDF/TheHive/issues/26)

**Fixed bugs:**

- Template Limit Bug [\#105](https://github.com/CERT-BDF/TheHive/issues/105)
- Bug related case [\#97](https://github.com/CERT-BDF/TheHive/issues/97)
- Case TLP should be set to AMBER by default [\#96](https://github.com/CERT-BDF/TheHive/issues/96)
- User is not notified on MISP error [\#88](https://github.com/CERT-BDF/TheHive/issues/88)
- Locked users cannot be assignee of cases [\#77](https://github.com/CERT-BDF/TheHive/issues/77)
- Task descriptions from case templates are not applied [\#65](https://github.com/CERT-BDF/TheHive/issues/65)
- Add an already exist observable returns an unexpected error [\#63](https://github.com/CERT-BDF/TheHive/issues/63)
- Don't use deleted obserables to link cases [\#62](https://github.com/CERT-BDF/TheHive/issues/62)
- Assign a default role to new users and remove the ability to assign empty roles [\#60](https://github.com/CERT-BDF/TheHive/issues/60)
- Locked users are still able to log in [\#59](https://github.com/CERT-BDF/TheHive/issues/59)
- MISP events counter is not refreshed [\#58](https://github.com/CERT-BDF/TheHive/issues/58)
- Make sure to clear new task log editor [\#57](https://github.com/CERT-BDF/TheHive/issues/57)
- Missing markdown editor in case close dialog [\#42](https://github.com/CERT-BDF/TheHive/issues/42)

**Closed issues:**

- Database schema update \(v8\) [\#67](https://github.com/CERT-BDF/TheHive/issues/67)
- Add support for more filetypes to PE\_info analyser [\#54](https://github.com/CERT-BDF/TheHive/issues/54)
- Create an analyzer to get information about PE file [\#51](https://github.com/CERT-BDF/TheHive/issues/51)
- PhishTank Analyzer [\#40](https://github.com/CERT-BDF/TheHive/issues/40)
- OTX Analyzer [\#32](https://github.com/CERT-BDF/TheHive/issues/32)

**Merged pull requests:**

- AlienVault OTX Analyzer [\#39](https://github.com/CERT-BDF/TheHive/pull/39) ([ecapuano](https://github.com/ecapuano))

## [2.9.2](https://github.com/CERT-BDF/TheHive/tree/2.9.2) (2017-01-19)
[Full Changelog](https://github.com/CERT-BDF/TheHive/compare/2.9.1...2.9.2)

**Implemented enhancements:**

- Feature Request - Add observable statistics [\#71](https://github.com/CERT-BDF/TheHive/issues/71)

**Fixed bugs:**

- docker image: $.post\(...\).success is not a function [\#95](https://github.com/CERT-BDF/TheHive/issues/95)

## [2.9.1](https://github.com/CERT-BDF/TheHive/tree/2.9.1) (2016-11-28)
**Implemented enhancements:**

- Statistics on a per case template name / prefix basis [\#31](https://github.com/CERT-BDF/TheHive/issues/31)
- Observable Viewing Page [\#17](https://github.com/CERT-BDF/TheHive/issues/17)
- Update logo and favicon [\#45](https://github.com/CERT-BDF/TheHive/issues/45)
- Inconsistent wording between the login and user management pages [\#44](https://github.com/CERT-BDF/TheHive/issues/44)
- MaxMind Analyzer 'Short Report' has hard-coded language [\#23](https://github.com/CERT-BDF/TheHive/issues/23)
- Don't update imported case from MISP if it is deleted or merged [\#22](https://github.com/CERT-BDF/TheHive/issues/22)
- Case merging [\#14](https://github.com/CERT-BDF/TheHive/issues/14)
- New analyzer to check URL categories [\#24](https://github.com/CERT-BDF/TheHive/pull/24) ([ecapuano](https://github.com/ecapuano))

**Fixed bugs:**

- Resource not found by Assets controller [\#38](https://github.com/CERT-BDF/TheHive/issues/38)
- NPE occurs at startup if conf directory doesn't exists [\#41](https://github.com/CERT-BDF/TheHive/issues/41)
- Systemd startup script does not work [\#29](https://github.com/CERT-BDF/TheHive/issues/29)
- MISP event parsing error when it doesn't contain any attribute [\#25](https://github.com/CERT-BDF/TheHive/issues/25)
- Phantom tabs [\#18](https://github.com/CERT-BDF/TheHive/issues/18)
- The Action button of observables list is blank [\#15](https://github.com/CERT-BDF/TheHive/issues/15)
- Description becomes empty when you cancel an edition [\#13](https://github.com/CERT-BDF/TheHive/issues/13)
- Metric Labels Not Showing in Case View [\#10](https://github.com/CERT-BDF/TheHive/issues/10)
- chrome on os x - header alignment [\#5](https://github.com/CERT-BDF/TheHive/issues/5)
- Tags not saving when creating observable. [\#4](https://github.com/CERT-BDF/TheHive/issues/4)

**Closed issues:**

- Statistics based on Tags [\#37](https://github.com/CERT-BDF/TheHive/issues/37)
- Give us something to work with! [\#2](https://github.com/CERT-BDF/TheHive/issues/2)

**Merged pull requests:**

- Fix "Run from Docker" [\#9](https://github.com/CERT-BDF/TheHive/pull/9) ([2xyo](https://github.com/2xyo))
- Fixing a Simple Typo [\#6](https://github.com/CERT-BDF/TheHive/pull/6) ([swannysec](https://github.com/swannysec))
- Fixed broken link to Wiki [\#1](https://github.com/CERT-BDF/TheHive/pull/1) ([Neo23x0](https://github.com/Neo23x0))



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*
