#!/bin/bash 

R CMD Rserve --RS-conf Rserve.conf --no-save
lein ring server-headless