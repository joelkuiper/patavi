#!/bin/bash
if [ "$(pidof Rserve)" ]
then
	echo "already running with (PID:" `pidof Rserve`")"
	exit 1
else
	workdir=`cd "tmp";pwd`
	nohup R CMD Rserve --RS-workdir ${workdir} --RS-conf resources/Rserve.conf --vanilla > logs/rserve.log 2>&1 &
	echo "starting with generated bootstrap.R (PID:" `pidof Rserve`")"
	exit 0
fi
