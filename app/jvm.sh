#!/bin/bash
BACKGROUND_RED=`tput setab 1`
BACKGROUND_GREEN=`tput setab 2`
BACKGROUND_BLUE=`tput setab 4`
TEXT_WHITE=`tput setaf 7`
RESET_FORMATTING=`tput sgr0`

jvmInit() {
  JVM_HOME=$HOME/.jvm
  if [ ! -d ${JVM_HOME} ]; then
    mkdir -p ${JVM_HOME}
  fi
  CACHE_FILE=${JVM_HOME}/.jvm-cache
  OLD_CACHE=${JVM_HOME}/.jvm-cache.old
  ARCHIVE_DIR=${JVM_HOME}/archives
  if [ ! -d ${ARCHIVE_DIR} ]; then
    mkdir -p ${ARCHIVE_DIR}
  fi

  OS=unknown
  PACKAGE=unknown
  case "$(uname)" in
      CYGWIN*)
          OS=win
          ;;
      Darwin*)
          OS=macos
          PACKAGE=dmg
          ;;
      Linux*)
          OS=linux
          which rpm > /dev/null && PACKAGE=rpm || PACKAGE=targz
          ;;
      SunOS*)
          OS=solaris
          ;;
      FreeBSD*)
          OS=bsd
  esac

  ARCH=unknown
  case "$(uname -m)" in
      x86_64)
          ARCH=x64
          ;;
      i386)
          OS=i386
          ;;
  esac

  TAGS="$OS,$ARCH,jdk,$PACKAGE"

}

jvmUpdateCache() {
  if [ "$1" = "silent" ]; then
    CHANGE_TIME=$(find ${CACHE_FILE} -mmin +1440 | wc -l)
    if [ ${CHANGE_TIME} = 1 ]; then
      if [ ! -f ${OLD_CACHE} ]; then
        cp ${CACHE_FILE} ${OLD_CACHE}
      fi
      curl -fjs -m 90 -A "jvm.sh/0.1" \
          "https://javaversionmanager.appspot.com/builds?tags=$TAGS" > ${CACHE_FILE} &
    fi
  else
    echo "TAGS: $TAGS"
    curl -j -m 90 -A "jvm.sh/0.1" \
        "https://javaversionmanager.appspot.com/builds?tags=$TAGS" > ${CACHE_FILE}
  fi
}

jvmListAvailable() {
  cat ${CACHE_FILE} | cut -d $'\t' -f 1 | column -x
}

jvmHelp() {
  echo "jvm"
  echo "   h       - Help"
  echo "   ls      - List installed versions"
  echo "   ls -a   - List available versions"
  echo "   update  - Update list of candidates"
  echo "   u <ver> - Use version"
  echo "   u       - Unset version"
  echo "   i <ver> - Install version"
  echo ""
}

jvmBefore() {
  echo "${BACKGROUND_RED}${TEXT_WHITE}-JAVA_HOME=${JAVA_HOME}${RESET_FORMATTING}" >&2
}
jvmAfter() {
  echo "${BACKGROUND_GREEN}${TEXT_WHITE}+JAVA_HOME=${JAVA_HOME}${RESET_FORMATTING}" >&2
}
jvmUnchanged() {
  echo "${BACKGROUND_BLUE}${TEXT_WHITE}JAVA_HOME=${JAVA_HOME}${RESET_FORMATTING}" >&2
}
jvmNotifyUpdates() {
  if [ -f ${OLD_CACHE} ]; then
    /usr/bin/diff ${OLD_CACHE} ${CACHE_FILE} > /dev/null
    if [ $? != 0 ]; then
      echo "There is a change in the available versions of java. Run 'jvm ls -a' to see available versions"
      rm ${OLD_CACHE}
    fi
  fi
}

macosToJava() {
  JDKDIR=$(basename $(dirname $(dirname $1)))
  JDKVER=$(echo ${JDKDIR}| sed -e "s/[A-Za-z]//g" | sed -e "s/^1.//g" | sed -e "s/\.$//g")
  if [ $(echo ${JDKVER} | grep -c _) = 0 ]; then
    JDKVER=${JDKVER}_0
  fi
  JDKVER=$(echo ${JDKVER} | sed -e "s/\.0_/u/g")
  printf "%10s   %s\n" "$JDKVER" "$1"
}

rpmToJava() {
  JDKDIR=$(basename $1)
  JDKVER=$(echo ${JDKDIR}| sed -e "s/[A-Za-z]//g" | sed -e "s/^1.//g" | sed -e "s/\.$//g")
  if [ $(echo ${JDKVER} | grep -c _) = 0 ]; then
    JDKVER=${JDKVER}_0
  fi
  JDKVER=$(echo ${JDKVER} | sed -e "s/\.0_/u/g")
  printf "%10s   %s\n" "$JDKVER" "$1"
}

macosListLocal() {
  find /Library/Java                            -name "Home" -type d 2>/dev/null| grep -i java
  find /System/Library/Java/JavaVirtualMachines -name "Home" -type d 2>/dev/null| grep -i java
}

macosInstallJava() {
  VER=$1

  URL=$(cat ${CACHE_FILE} | grep ^${VER} | cut -d $'\t' -f 4 | sed -e 's/otn/otn-pub/g' | sed -e 's/otn-pub-pub/otn-pub/g')
  JDKFILE=$(basename ${URL})
  echo "Will download from $URL to $ARCHIVE_DIR/$JDKFILE"

  curl --junk-session-cookies \
      -L -b "oraclelicense=a" "$URL" -o "$ARCHIVE_DIR/$JDKFILE"

  MOUNTDIR=$(echo $(hdiutil mount ${ARCHIVE_DIR}/"$JDKFILE" | tail -1 | awk '{$1=$2=""; print $0}') | xargs -0 echo)
  sudo installer -pkg "$MOUNTDIR/"*.pkg -target /
  hdiutil unmount "$MOUNTDIR"
}

rpmInstallJava() {
  VER=$1

  URL=$(cat ${CACHE_FILE} | grep ^${VER} | cut -d $'\t' -f 4 | sed -e 's/otn/otn-pub/g' | sed -e 's/otn-pub-pub/otn-pub/g')
  JDKFILE=$(basename ${URL})
  echo "Will download from $URL to $ARCHIVE_DIR/$JDKFILE"

  curl --junk-session-cookies \
      -L -b "oraclelicense=a" "$URL" -o "$ARCHIVE_DIR/$JDKFILE"

  sudo rpm -ivh ${ARCHIVE_DIR}/${JDKFILE}
}

rpmListLocal() {
  find /usr/java/ -maxdepth 1 -mindepth 1 ! -type l
}

jvmInstallJava() {
  if [ ${OS} = macos ]; then
    macosInstallJava $1
  elif [ ${OS} = linux ]; then
    if [ ${PACKAGE} = rpm ]; then
      rpmInstallJava $1
    else
      echo "Cannot install on non rpm yet"
    fi
  else
      echo "Cannot install on other oses"
  fi
}

findJava() {
  jvmLs | grep $1 | head -1 | tr -s " " | cut -d " " -f 3
}

jvmLs() {
  if [ ${OS} = macos ]; then
    macosListLocal | while read -r JAVALINE; do macosToJava "$JAVALINE"; done
  elif [ ${OS} = linux ]; then
    if [ ${PACKAGE} = rpm ]; then
      rpmListLocal | while read -r JAVALINE; do rpmToJava "$JAVALINE"; done
    else
      echo "Cannot ls on non rpm yet"
    fi
  else
      echo "Cannot ls on other oses"
  fi
}

jvmInit

COMMAND=$1
if [ "$COMMAND" = "" ]; then
  COMMAND=h
fi
case "$COMMAND" in
  h)
    jvmHelp
    jvmUnchanged
    jvmNotifyUpdates
    ;;
  updateSilent)
    jvmUpdateCache silent
    ;;
  update)
    jvmUpdateCache
    echo ""
    jvmListAvailable
    echo ""
    jvmUnchanged
    jvmNotifyUpdates
    ;;
  ls)
    if [ "$2" = "-a" ]; then
      jvmListAvailable
    else
      jvmLs | sort -k 1 -n
    fi
    echo ""
    jvmUnchanged
    jvmNotifyUpdates
    jvmUpdateCache silent
    ;;
  i)
    jvmInstallJava $2
    jvmNotifyUpdates
    jvmUpdateCache silent
    ;;
  u)
    jvmBefore
    if [ "$2" = "" ]; then
        unset JAVA_HOME
    else
        export JAVA_HOME=$(findJava $2)
    fi
    jvmAfter
    jvmNotifyUpdates
    jvmUpdateCache silent
    ;;
  *)
    echo "Unknown command '$COMMAND'"
    jvmHelp
    jvmUnchanged
    jvmNotifyUpdates
    jvmUpdateCache silent
    ;;
esac
