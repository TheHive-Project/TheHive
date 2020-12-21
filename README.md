<div>
  <p align="center">
    <img src="images/thehive-logo.png"width="600"/>  
  </p>
</div>
<div>
  <p align="center">
    <a href="https://chat.thehive-project.org" target"_blank"><img src="https://img.shields.io/badge/chat-on%20discord-7289da.svg?sanitize=true" alt="Discord"></a>
    <a href><img src="https://drone.strangebee.com/api/badges/TheHive-Project/TheHive/status.svg?ref=refs/heads/master-th4" alt="Build status"></a>
    <a href="./LICENSE" target"_blank"><img src="https://img.shields.io/github/license/TheHive-Project/TheHive" alt="License"></a>        
  </p>
</div>

[TheHive](https://thehive-project.org/) is a scalable 3-in-1 open source and free Security Incident Response Platform designed to make life easier for SOCs, CSIRTs, CERTs and any information security practitioner dealing with security incidents that need to be investigated and acted upon swiftly. It is the perfect companion to [MISP](http://www.misp-project.org/). You can synchronize it with one or multiple MISP instances to start investigations out of MISP events. You can also export an investigation's results as a MISP event to help your peers detect and react to attacks you've dealt with. Additionally, when TheHive is used in conjunction with [Cortex](https://github.com/TheHive-Project/Cortex/), security analysts and researchers can easily analyze tens if not hundred of observables.  

![Current Cases View](images/Current_cases.png)

## Collaborate
Collaboration is at the heart of TheHive.

Multiple analysts from one organisations can work together on the same case simultaneously. For example, an analyst may deal with malware analysis while another may work on tracking C2 beaconing activity on proxy logs as soon as IOCs have been added by their coworker. Using TheHive's live stream, everyone can keep an eye on what's happening on the platform, in real time.

Multi-tenancy and fine grained user profiles let organisations and analysts work and collaborate on a same case accross organisations. For example, one case can be created by a first organisation who start investigating and ask for contribution from other teams or escalate to another organisation.

## Elaborate
Within TheHive, every investigation corresponds to a case. Cases can be created from scratch or from [MISP](http://www.misp-project.org/) events, SIEM alerts, email reports and any other noteworthy source of security events.

Each case can be broken down into one or more tasks. Instead of adding the same tasks to a given type of case every time one is created, analysts can use TheHive's template engine to create them once and for all. Case templates can also be used to associate metrics to specific case types in order to drive the team's activity, identify the type of investigations that take significant time and seek to automate tedious tasks.

Each task can be assigned to a given analyst. Team members can also take charge of a task without waiting for someone to assign it to them.

Tasks may contain multiple work logs that contributing analysts can use to describe what they are up to, what was the outcome, attach pieces of evidence or noteworthy files and so on. Logs can be written using a rich text editor or Markdown.

## Analyze
You can add one or hundreds if not thousands of observables to each case you create. You can also create a case out of a [MISP](http://www.misp-project.org/) event. TheHive can be very easily linked to one or several MISP instances and MISP events can be previewed to decide whether they warrant an investigation or not. If an investigation is in order, the analyst can then add the event to an existing case or import it as a new case using a customizable template.

Thanks to [TheHive4py](https://thehive-project.org/#section_thehive4py), TheHive's Python API client, it is possible to send SIEM alerts, phishing and other suspicious emails and other security events to TheHive. They will appear in its `Alerts` panel along with new or updated MISP events, where they can be previewed, imported into cases or ignored.

![The Alerts Pane](images/Alerts_Panel.png)

TheHive has the ability to automatically identify observables that have been already seen in previous cases. Observables can also be associated with a TLP and the source which provided or generated them using tags. The analyst can also easily mark observables as IOCs and isolate those using a search query then export them for searching in a SIEM or other data stores.

Analysts can analyze tens or hundreds of observables in a few clicks by leveraging the analyzers of one or several [Cortex](https://github.com/TheHive-Project/Cortex/) instances depending on your OPSEC needs: DomainTools, VirusTotal, PassiveTotal, Joe Sandbox, geolocation, threat feed lookups and so on.

Security analysts with a knack for scripting can easily add their own analyzers to Cortex in order to automate actions that must be performed on observables or IOCs. They can also decide how analyzers behave according to the TLP. For example, a file added as observable can be submitted to VirusTotal if the associated TLP is WHITE or GREEN. If it's AMBER, its hash is computed and submitted to VT but not the file. If it's RED, no VT lookup is done.

# Try it
To try TheHive, you can use the [training VM](https://github.com/TheHive-Project/TheHiveDocs/blob/master/training-material.md) or install it by reading the [Installation Guide](https://github.com/TheHive-Project/TheHiveDocs/tree/master/TheHive4/Installation).

# Details

## Documentation
We have made several guides available in the [Documentation repository](https://github.com/TheHive-Project/TheHiveDocs/tree/master/TheHive4).



## Main features

### Multi-tenancy
TheHive comes with a special multi-tenancy support. It allows the following strategies:

- Use a siloed multi-tenancy: many organisations can be defined without allowing them to share data;
- Use a collaborative multi-tenancy: a set of organisations can be allowed to collaborate on specific cases/tasks/observables, using custom defined user profiles (RBAC).


### RBAC
TheHive comes with a set of permissions and several pre-configured user profiles: 

- `admin`: full administrative permissions on the plateform ; can't manage any Cases or other data related to investigations;
- `org-admin`: manage users and all organisation-level configuration, can create and edit Cases, Tasks, Observables and run Analyzers and Responders;
- `analyst`: can create and edit _Cases_, _Tasks_, _Observables_ and run _Analyzers_ & _Responders_;
- `read-only`: Can only read, Cases, Tasks and Observables details;

New profiles can be created by administrators of the platform. 

### Authentication
TheHive 4 supports authentication methods:

- local accounts
- Active Directory
- LDAP
- Basic Auth
- API keys
- OAUTH2
- Multi Factor Authentication

### Statistics & Dashboards
TheHive comes with a powerful statistics module that allows you to create meaningful dashboards to drive your activity and support your budget requests.

## Integrations 

### MISP and Cortex
TheHive can be configured to import events from one or multiple [MISP](http://www.misp-project.org/) instances. You can also use TheHive to export cases as MISP events to one or several MISP servers. 

[Cortex](https://github.com/TheHive-Project/Cortex/) is the perfect companion for TheHive. Use one or several to analyze observables at scale.

### Integration with Digital Shadows
TheHive Project provides [DigitalShadows2TH](https://github.com/TheHive-Project/DigitalShadows2TH), a free, open source [Digital Shadows](https://www.digitalshadows.com/) alert feeder for TheHive. You can use it to import Digital Shadows *incidents* and *intel-incidents* as alerts in TheHive, where they can be previewed and transformed into new cases using pre-defined incident response templates or added into existing ones.

### Integration with Zerofox
[Zerofox2TH](https://github.com/TheHive-Project/Zerofox2TH) is a free, open source [ZeroFOX](https://www.zerofox.com/) alert feeder for TheHive, written by TheHive Project. You can use it to feed ZeroFOX alerts into TheHive, where they can be previewed and transformed into new cases using pre-defined incident response templates or added into existing ones.

### And many more

Lots of **awesome** integrations shared by the community could be listed there. If you're looking for a specific one, **a  dedicated repository** containing all known details and references about existing integrations is updated frequently, and can be found here: [https://github.com/TheHive-Project/awesome](https://github.com/TheHive-Project/awesome).

# License
TheHive is an open source and free software released under the [AGPL](https://github.com/TheHive-Project/TheHive/blob/master/LICENSE) (Affero General Public License). We, TheHive Project, are committed to ensure that TheHive will remain a free and open source project on the long-run.

# Updates
Information, news and updates are regularly posted on [TheHive Project Twitter account](https://twitter.com/thehive_project) and on [the blog](https://blog.thehive-project.org/).

# Contributing
Please see our [Code of conduct](code_of_conduct.md). We welcome your contributions. Please feel free to fork the code, play with it, make some patches and send us pull requests via [issues](https://github.com/TheHive-Project/TheHive/issues).

# Support
Please [open an issue on GitHub](https://github.com/TheHive-Project/TheHive/issues) if you'd like to report a bug or request a feature. We are also available on [Discord](https://chat.thehive-project.org) to help you out.

If you need to contact the project team, send an email to <support@thehive-project.org>.

**Important Note**:

- If you have problems with [TheHive4py](https://github.com/TheHive-Project/TheHive4py), please [open an issue on its dedicated repository](https://github.com/TheHive-Project/TheHive4py/issues/new).
- If you encounter an issue with Cortex or would like to request a Cortex-related feature, please [open an issue on its dedicated GitHub repository](https://github.com/TheHive-Project/Cortex/issues/new).
- If you have troubles with a Cortex analyzer or would like to request a new one or an improvement to an existing analyzer, please open an issue on the [analyzers' dedicated GitHub repository](https://github.com/TheHive-Project/cortex-analyzers/issues/new).

# Community Discussions
We have set up a Google forum at <https://groups.google.com/a/thehive-project.org/d/forum/users>. To request access, you need a Google account. You may create one [using a Gmail address](https://accounts.google.com/SignUp?hl=en) or [without it](https://accounts.google.com/SignUpWithoutGmail?hl=en).

# Website
<https://thehive-project.org/>
