#!/bin/bash

if [ $# -lt 3 ] || [ $# -gt 4 ]; then
  cat <<EOF

  run.sh unload DBNAME SQL|SQLFILE
         load   DBNAME excelfile [tabname]

EOF
  exit 1
fi

DBNAME=${2:-"testdb"}
OPTTYPE=${1:-"unload"}
if [ x"${OPTTYPE}" = "xunload" ]; then
  SQLSTR=$3
  if [ -s "${SQLSTR}" ]; then
    FROMFILE=1
    if [ ! x"${SQLSTR:0:1}" = x"/" ]; then
      SQLSTR=$(pwd)/${SQLSTR}
    fi
  fi
else
  EXCELFILE=$3
  TABNAME=$4
fi

WORKDIR=$(cd $(dirname $0) && pwd)
OUTDIR=/tmp
PROP=user

JDBCJAR=$(ls ${WORKDIR}/lib/gbasedbtjdbc*.jar 2>/dev/null)
if [ x"${JDBCJAR}" = x ]; then
  echo "JDBC Driver not found!"
  exit 1
fi

if [ x"${OPTTYPE}" = "xunload" ]; then
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
else
  java -Dfile.encoding=UTF-8 \
    -DPROP="${PROP:-user}" \
    -DDBNAME="${DBNAME}" \
    -DTABNAME="${TABNAME}" \
    -DEXCELFILE="${EXCELFILE}" \
    -cp ${WORKDIR}/conf/:${WORKDIR}/lib/* \
    com.gbasedbt.POIImport
fi

exit 0
