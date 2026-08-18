#!/bin/bash

if [ $# -eq 1 ]; then
  USERPROPERTY=$1
fi

USERPROPERTY=${USERPROPERTY:-"user"}

JDBCJAR=$(ls gbasedbt*.jar 2>/dev/null)
if [ x"${JDBCJAR}" = x ]; then
  echo "JDBC Driver not found!"
  exit 1
fi

TESTQUERY=$(ls TestDbOpt.class 2>/dev/null)
if [ x"${TESTQUERY}" = x ]; then
  TESTJAVA=$(ls TestDbOpt.java 2>/dev/null)
  if [ x"${TESTJAVA}" = x ]; then
    echo "TestDbOpt.java not found!"
    exit 2
  fi
  javac TestDbOpt.java 2>/dev/null
  if [ $? -ne 0 ]; then
    echo "javac TestDbOpt.java error!"
    exit 3
  fi
fi

java -Dfile.encoding=UTF-8 -cp .:${JDBCJAR} -Dprop=${USERPROPERTY} TestDbOpt

exit 0
