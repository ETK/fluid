#!/usr/bin/env bash
#
# Deploy a jar, source jar, and javadoc jar to Sonatype's snapshot repo.
#

REPO="cescoffier/fluid"
BRANCH="master"

set -e

if [ "$TRAVIS_REPO_SLUG" != "$REPO" ]; then
  echo "Skipping snapshot deployment: wrong repository. Expected '$REPO' but was '$TRAVIS_REPO_SLUG'."
elif [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo "Skipping snapshot deployment: was pull request."
elif [ "$TRAVIS_BRANCH" != "$BRANCH" ]; then
  echo "Skipping snapshot deployment: wrong branch. Expected '$BRANCH' but was '$TRAVIS_BRANCH'."
else
  echo "Deploying snapshot..."
  mvn clean source:jar javadoc:jar deploy --settings=".build/settings.xml" -Dmaven.test.skip=true
  echo "Snapshot deployed!"
fi