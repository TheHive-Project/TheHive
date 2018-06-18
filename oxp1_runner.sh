#!/bin/sh

rm /home/david/git/onysys_repository/OXP1/processes/TheHive/target/universal/stage/RUNNING_PID

export JAVA_OPTS='-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005'

cd /home/david/git/onysys_repository/OXP1/processes/TheHive/target/universal/stage/bin
./thehive -Dconfig-file=/etc/thehive/application.conf
