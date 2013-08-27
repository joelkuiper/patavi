<img src="https://raw.github.com/joelkuiper/patavi/gh-pages/assets/img/patavi_small.png" alt="logo" align="right" width="250" />
# Expose R as WAMP

**This is an alpha release.  We are using it internally in production,
  but the API and organizational structure are subject to change.
  Comments and suggestions are much appreciated.**

## Introduction
Patavi is a distributed system for exposing
R scripts as web services (through [WAMP](http://wamp.ws/) RPC).
It was created out of the need to run
potentially very long running R scripts in a web browser while
providing an interface to see the status updates. We currently use it
to do Multi Criteria Decision Analysis (MCDA), see [our
demo](http://mcda.clinici.co). It is written in Clojure.

## Alternatives
If you are looking for just a web-based interactive R environment
checkout [RStudio Shiny](http://www.rstudio.com/shiny/). If you just
want to expose R scripts as HTTP see
[FastRWeb](https://www.rforge.net/FastRWeb/) or one of the [many other
options](http://cran.r-project.org/doc/FAQ/R-FAQ.html#R-Web-Interfaces).


## Usage
Start the server with `lein run` in the server folder then start one or more workers
with `lein run` you can provide the method name and file in the options (run
`lein --help` for details).

The R script takes exactly one argument `params` which is the deserialzed JSON
(through [RJSONIO](http://cran.r-project.org/web/packages/RJSONIO/index.html)). The following script emulates a long running process:

    slow <- function(params) {
      N <- 100;
      x <- abs(rnorm(N, 0.001, 0.05))
      for(i in as.single(1:N)) {
        update(i); # send an out of band progress update
        Sys.sleep(x[[i]])
      }

      # The plot will be converted to base64 (url encoded)
      save.plot(function() hist(x), "duration", type="png")

      params
    }

The server is exposed as WAMP which can be accessed with, for example,
[Autobahn](http://autobahn.ws/) (see `client` folder for an example using
[AngularJS](http://www.angularjs.org/)).

## Installation

### Method 1 (Chef cookbook)
The simplest way to
try out Patavi is in a vagrant virtual machine provisioned with our
Chef cookbook. This has been tested on Ubuntu 12.04 and 13.04, but
should also work on Mac OS X provided you know how to get the
dependencies through [Homebrew](http://brew.sh/).

You'll need to:

Install [Vagrant](http://www.vagrantup.com/) from their site and
install [Berkshelf](http://berkshelf.com/) with:

     sudo apt-get install ruby-dev gem libxslt-dev gem install
     berkshelf

     # Install the vagrant-berkshelf plugin
     vagrant plugin install vagrant-berkshelf

Clone the cookbook and run the Virtual Machine

     git clone https://github.com/joelkuiper/patavi-cookbook.git cd
     patavi-cookbook vagrant up

Now you have time to grab a cup of coffee, go out with friends, find
the love of your life, climb the mount everest or see the aurora
borealis. The demo can be started by running the client
(for example with `Python -m SimpleHTTPServer`)
and pointing the configuration to `33.33.33.10`, or whatever you set the Vagrantfile to.

Note that the base box is Ubuntu 12.04 (precise) 64-bit. For 32-bit
change the variable accordingly in the Vagrantfile

### Method 2 (manual)
To manually set up an environment
you'll need the following set-up and configured:

* R (with RJSONIO, RServe(>= 1.7) and for image support Cairo and base64enc)
* Java (>= 1.7)
* ZeroMQ (preferably >= 3.0)
* Leiningen (> 2.0)

clone the repository and `lein install` the common folder. Then start
the server and worker by running `lein run --`, see `'lein run --
--help` for options
