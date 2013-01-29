#!/bin/bash 

R CMD Rserve --RS-conf Rserve.conf
lein ring server-headless