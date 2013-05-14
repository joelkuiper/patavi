#!/bin/bash
if [ "$(pidof Rserve)" ]
then
	exit 1
else
	nohup R CMD Rserve --RS-conf resources/Rserve.conf --vanilla > logs/rserve.log 2> logs/rserve.err < /dev/null &
	echo "done"
	exit 0
fi
