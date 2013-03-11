#!/bin/bash

lein marg
cp docs/uberdoc.html /tmp/index.html
git stash
git checkout gh-pages
mv /tmp/index.html . 
git commit -a -m "Update documentation" 
git push origin gh-pages
git checkout master
git stash pop