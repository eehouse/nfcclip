#!/bin/sh

set -e -u -x

WD=$(cd $(dirname $0)/.. && pwd)
APP=$(basename $WD)
echo $APP

DIR=/tmp/${APP}_$$
cd $(dirname $DIR)
git clone $WD $DIR
cd $DIR


./gradlew asDeb
