#!/bin/bash

debug=false
repeat=1
args=
numargs=0

while true;
do
  case $1 in
    "")
      break
      ;;
    -d) 
      debug="true"
      shift
      ;;
    -r)
      shift
      repeat=$1
      shift
      ;;
    *)
      args="$args $1"
      numargs=$(($numargs + 1))
      shift
      ;;
  esac
done

if [ $numargs -ne 2 ]
then
  echo 'Expected arguments: [port, depth]; not:' $args
  exit 1
fi 


bin/sessionj -cp tests/classes/ pldi.benchmarks.benchmark2.a.NoAlias $debug $args $repeat  
