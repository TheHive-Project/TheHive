# Log

## Model definition

Required attributes:
 - `message` (text) : content of the Log
 - `startDate` (date) : date of the log submission **default=now**
 - `status` (logStatus) : status of the log (*Ok* or *Deleted*) **default=Ok**

Optional attributes:
 - `attachment` (attachment) : file attached to the log

## Log manipulation

### Log methods

|HTTP Mehod |URI                                     |Action                                |
|-----------|----------------------------------------|--------------------------------------|
|GET        |/api/case/task/:taskId/log              |Get logs of the task                  |
|POST       |/api/case/task/log/_search              |Find logs                             |
|POST       |/api/case/task/:taskId/log              |Create a log                          |
|PATCH      |/api/case/task/log/:logId               |Update a log                          |
|DELETE     |/api/case/task/log/:logId               |Remove a log                          |
|GET        |/api/case/task/log/:logId               |Get a log                             |

### Create a log
The URL used to create a task is:
```
POST /api/case/task/<<taskId>>/log
```
\<\<taskId\>\> must be replaced by task id

Required log attributes (cf. models) must be provided.

This call returns attributes of the created log.

#### Examples
Creation of a simple log in task `AVqqeXc9yQ6w1DNC8aDj`:
```
curl -XPOST -u myuser:mypassword -H 'Content-Type: application/json' http://127.0.0.1:9000/api/case/task/AVqqeXc9yQ6w1DNC8aDj/log -d '{
  "message": "Some message"
}'
```
It returns:
```
{
  "startDate": 1488919949497,
  "createdBy": "admin",
  "createdAt": 1488919949495,
  "user": "myuser",
  "message":"Some message",
  "status": "Ok",
  "id": "AVqqi3C-yQ6w1DNC8aDq",
  "type":"case_task_log"
}
```

If log contain attachment, request must be in multipart format:
```
curl -XPOST -u myuser:mypassword http://127.0.0.1:9000/api/case/AVqqdpY2yQ6w1DNC8aDh/task -F '_json={"message": "Screenshot of fake site"};type=application/json' -F 'attachment=@screenshot1.png;type=image/png'
```
It returns:
```
{
  "createdBy": "myuser",
  "message": "Screenshot of fake site",
  "createdAt": 1488920587391,
  "startDate": 1488920587394,
  "user": "myuser",
  "status": "Ok",
  "attachment": {
    "name": "screenshot1.png",
    "hashes": [
      "086541e99743c6752f5fd4931e256e6e8d5fc7afe47488fb9e0530c390d0ca65",
      "8b81e038ae0809488f20b5ec7dc91e488ef601e2",
      "c5883708f42a00c3ab1fba5bbb65786c"
    ],
    "size": 15296,
    "contentType": "image/png",
    "id": "086541e99743c6752f5fd4931e256e6e8d5fc7afe47488fb9e0530c390d0ca65"
  },
  "id": "AVqqlSy0yQ6w1DNC8aDx",
  "type": "case_task_log"
}
```
