#!/bin/bash

# From https://github.com/twitter/util

SBT_VER=1.2.1
SBT_JAR=sbt-launch.jar
SBT_SHA128=9e997833c7980b71feaae552bf454f292268c4ee

SBT_REPO="https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch"

if [ ! -f ${SBT_JAR} ]; then
  echo "downloading ${PWD}/${SBT_JAR}" 1>&2
  curl --location --silent --fail --remote-name ${SBT_REPO}/${SBT_VER}/${SBT_JAR} || exit 1
fi

CHECKSUM=`openssl dgst -sha1 ${SBT_JAR} | awk '{ print $2 }'`
if [ "${CHECKSUM}" != ${SBT_SHA128} ]
then
  echo "bad ${PWD}/${SBT_JAR}.  delete ${PWD}/${SBT_JAR} and run $0 again."
  exit 1
fi

[ -f ~/.sbtconfig ] && . ~/.sbtconfig

java -ea                          \
  ${SBT_OPTS}                     \
  ${JAVA_OPTS}                    \
  -Djava.net.preferIPv4Stack=true \
  -XX:+AggressiveOpts             \
  -XX:+UseParNewGC                \
  -XX:+UseConcMarkSweepGC         \
  -XX:+CMSParallelRemarkEnabled   \
  -XX:+CMSClassUnloadingEnabled   \
  -XX:ReservedCodeCacheSize=128m  \
  -XX:SurvivorRatio=128           \
  -XX:MaxTenuringThreshold=0      \
  -Xss8M                          \
  -Xms512M                        \
  -Xmx2G                          \
  -server                         \
  -jar ${SBT_JAR} "$@"
