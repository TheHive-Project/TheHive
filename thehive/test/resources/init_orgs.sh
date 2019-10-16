#!/usr/bin/env bash

declare -A PROFILES
PROFILES[admin]="org-admin"
PROFILES[user]="incidentHandler"
PROFILES[ro]="read-only"

for ORG in cert csirt soc
do
  echo -n "Create organisation $ORG ... "
  curl -s -w '%{http_code}\n' -o /dev/null -uadmin:secret http://127.0.0.1:9000/api/organisation -H 'Content-type: application/json' -d '{"name": "'$ORG'", "description": "'$ORG'"}'
  for USER in admin user ro
  do
    echo -n "Create user $ORG$USER in $ORG ... "
    curl -s -w '%{http_code}\n' -o /dev/null -uadmin:secret http://127.0.0.1:9000/api/v1/user -H 'Content-type: application/json' -d '{"login":"'$ORG$USER'@thehive.local","name":"'$ORG$USER'","profile":"'${PROFILES[$USER]}'","organisation":"'$ORG'"}'
    echo -n "Set password $ORG$USER to user $ORG$USER ... "
    curl -s -w '%{http_code}\n' -o /dev/null -uadmin:secret http://127.0.0.1:9000/api/v1/user/$ORG${USER}@thehive.local/password/set -H 'Content-type: application/json' -H "X-Organisation: $ORG" -d '{"password": "'$ORG$USER'"}'
  done
  for I in $(seq 1 10)
  do
    echo -n "Create case $I in $ORG ... "
    curl -s -w '%{http_code}\n' -o /dev/null -u${ORG}user:${ORG}user http://127.0.0.1:9000/api/case -H 'Content-type: application/json' -H "X-Organisation: $ORG" -d '{"title": "case #'$I' ('$ORG')", "description": "created automatically"}'
  done
  for I in $(seq 1 10)
  do
    echo -n "Create alert $I in $ORG ... "
    curl -s -w '%{http_code}\n' -o /dev/null -u${ORG}user:${ORG}user http://127.0.0.1:9000/api/alert -H 'Content-type: application/json' -H "X-Organisation: $ORG" -d '{"type": "init", "source": "init_script", "sourceRef": "alert_'$ORG'_'$I'", "title": "alert #'$I' ('$ORG')", "description": "An alert", "date": '$(date +%s)000'}'
  done
done


