#!/bin/bash
if [ "$(pidof Rserve)" ]
then
	exit 1
else
	nohup R CMD Rserve --RS-conf resources/Rserve.conf --vanilla > r.log 2> r.err < /dev/null &
	echo "done"
	exit 0
fi
