#!/bin/bash

R CMD Rserve --RS-conf resources/Rserve.conf --no-save
lein ring server-headless