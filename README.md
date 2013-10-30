<img src="https://raw.github.com/joelkuiper/patavi/gh-pages/assets/img/patavi_small.png" alt="logo" align="right" width="250" />

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
with `lein run`. You can provide the method name and file in the options (run
`lein --help` for details).

The R script takes exactly one argument `params` which is the de-serialized JSON
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
To manually set up an environment
you'll need the following set-up and configured:

* R (with RJSONIO, RServe(>= 1.7) and for image support Cairo and base64enc)
* Java (>= 1.7)
* ZeroMQ (preferably >= 3.0)
* Leiningen (> 2.0)

clone the repository and `lein install` the common folder. Then start
the server and worker by running `lein run`, see `lein run --
--help` for options


## Performance
Using [criterium](https://github.com/hugoduncan/criterium) to benchmark an echo service (one worker) the following results were obtained:

*Evaluation count : 3480 in 60 samples of 58 calls.*

| Name                          | Measurement          |
| _____________________________ | ____________________ |
| Execution time mean           | 16.029490 ms         |
| Execution time std-deviation  | 2.065263 ms          |
| Execution time lower quantile | 12.122101 ms ( 2.5%) |
| Execution time upper quantile | 19.261436 ms (97.5%) |
| Overhead used                 | 1.058769 ns          |

Note, however, that the 16 ms is a severe underestimate since it does
not take into account network latency or the overhead of serializing /
deserializing the input data.

## Licence
Copyright 2013 Joel Kuiper and other contributors
http://patavi.com/

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
