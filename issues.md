# stats on task return wrong values:
```
curl 'http://127.0.0.1:9000/api/case/task/_stats' -H 'Content-Type: application/json;charset=UTF-8' -d '
{
  "query": {
    "_and": [{
      "_parent": {
        "_type": "case",
        "_query": {
          "_id": "4320"
        }
      }
    }, {
      "_not": {
        "status": "Cancel"
      }
    }]
  },
  "stats": [{
    "_agg": "field",
    "_field": "status",
    "_select": [{
      "_agg": "count"
    }]
  }, {
    "_agg": "count"
  }]
}'
```
The filter `_parent` is ignored.

# Handle BadConfiguration in errorHandler 