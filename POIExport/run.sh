#!/bin/bash

if [ $# -lt 3 ] || [ $# -gt 4 ]; then
  cat <<EOF

  run.sh unload TYPE   DBNAME    SQL|SQLFILE
         load   DBNAME fileName [tabName]

EOF
  exit 1
fi

OPTTYPE=${1:-"unload"}
if [ x"${OPTTYPE}" = x"unload" ]; then
  FILETYPE=${2:-"xlsx"}
  DBNAME=${3:-"testdb"}
  SQLSTR=$4
  if [ -s "${SQLSTR}" ]; then
    FROMFILE=1
    if [ ! x"${SQLSTR:0:1}" = x"/" ]; then
      SQLSTR=$(pwd)/${SQLSTR}
    fi
  fi
else
  DBNAME=${2:-"testdb"}
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
      -DOUTTYPE="${FILETYPE}" \
      -cp ${WORKDIR}/conf/:${WORKDIR}/lib/* \
      com.gbasedbt.POIExport
  else
    java -Dfile.encoding=UTF-8 \
      -DPROP="${PROP:-user}" \
      -DDBNAME="${DBNAME}" \
      -DSQLFILE="${SQLSTR}" \
      -DOUTDIR="${OUTDIR}" \
      -DOUTTYPE="${FILETYPE}" \
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
