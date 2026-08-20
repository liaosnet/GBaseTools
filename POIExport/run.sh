#!/bin/bash

if [ $# -ne 2 ]; then
  cat <<EOF

  run.sh DBNAME SQL

EOF
  exit 1
fi

DBNAME=${1:-"testdb"}
SQLSTR=$2

if [ -s "${SQLSTR}" ]; then
  FROMFILE=1
  if [ ! x"${SQLSTR:0:1}" = x"/" ]; then
    SQLSTR=$(pwd)/${SQLSTR}
  fi
fi

WORKDIR=$(cd $(dirname $0) && pwd)
OUTDIR=/tmp
PROP=user

JDBCJAR=$(ls ${WORKDIR}/lib/gbasedbtjdbc*.jar 2>/dev/null)
if [ x"${JDBCJAR}" = x ]; then
  echo "JDBC Driver not found!"
  exit 1
fi

if [ ${FROMFILE:-0} -eq 0 ]; then
  java -Dfile.encoding=UTF-8 \
    -DPROP="${PROP:-user}" \
    -DDBNAME="${DBNAME}" \
    -DSQL="${SQLSTR}" \
    -DOUTDIR="${OUTDIR}" \
    -cp ${WORKDIR}/conf/:${WORKDIR}/lib/* \
    com.gbasedbt.POIExport
else
  java -Dfile.encoding=UTF-8 \
    -DPROP="${PROP:-user}" \
    -DDBNAME="${DBNAME}" \
    -DSQLFILE="${SQLSTR}" \
    -DOUTDIR="${OUTDIR}" \
    -cp ${WORKDIR}/conf/:${WORKDIR}/lib/* \
    com.gbasedbt.POIExport
fi

exit 0
