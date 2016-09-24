#!/bin/sh
set -e

if [ "$TRAVIS_PULL_REQUEST" = false ]; then
  if [ "$TRAVIS_BRANCH" = "master" ]; then
    cat .appcfg_oauth2_tokens_java | sed -e "s/rahul/$(whoami)/g" > $HOME/.appcfg_oauth2_tokens_java
    ./gradlew clean test && ./gradlew appengineUpdateAll
  else
    ./gradlew clean check
  fi
else
  ./gradlew clean check
fi
