# Authentication

Most API calls requires authentication. Credentials can be provided using a session cookie or directly using HTTP basic
authentication. (API key is not usable in the current version of TheHive, due to a rethinking of service account
**TODO need issue reference**).

Session cookie is suitable for browser authentication, not for a dedicated tool. The easiest solution if you want to
write a tool that use TheHive API is to use basic authentication. For example, to list cases, use the following curl
command:
```
curl -u mylogin:mypassword http://127.0.0.1:9000/api/cases
```
