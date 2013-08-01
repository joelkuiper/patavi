'use strict';

/* Services */
angular.module('clinicico', []).
  value('version', '0.1').
  factory('clinicico.tasks', function ($q, $rootScope, $http) {

  var clinicico = {};
  // Load default config if none found
  if(!angular.isDefined(window.clinicico)) {
    clinicico = angular.extend(clinicico, { config: { baseUrl: "http://localhost:3000/tasks/" } })
  } else {
    clinicico = window.clinicico;
  }

  var Task = function(method, data) {
    var self = this;
    var scope = $rootScope.$new(true);
    var resultsFuture = $q.defer();

    this.method = method;
    this.url = clinicico.config.baseUrl + method;
    this.results = resultsFuture.promise;
    this.lastStatus = null;

    this.on = function(eventName, callback) {
      scope.$on(eventName, function(e, data) {
        self.status = data;
        callback(data);
      });
    }

    /* Needed because WebSocket does not trigger
       a digest but $http does
       see https://coderwall.com/p/ngisma */
    scope.safeApply = function(fn) {
      var phase = this.$root.$$phase;
      if(phase == '$apply' || phase == '$digest') {
        if(fn && (typeof(fn) === 'function')) {
          fn();
        }
      } else {
        this.$apply(fn);
      }
    };

    /* Thanks to Modernizr
     https://github.com/Modernizr/Modernizr/blob/master/feature-detects/websockets/binary.js */
    function hasWebsockets() {
      var protocol = 'https:'==location.protocol?'wss':'ws',
      protoBin;

      if("WebSocket" in window) {
        if( protoBin = "binaryType" in WebSocket.prototype ) {
          return protoBin;
        }
        try {
          return !!(new WebSocket(protocol+'://.').binaryType);
        } catch (e){}
      }

      return false;
    }


    function update(eventName, data) {
      scope.safeApply(function() {
        scope.$broadcast(eventName, data);
        self.lastStatus = data;

        if(data.status === "failed") {
          resultsFuture.reject(data);
        }

        if(data.results) {
          angular.forEach(data._links, function(link) {
            if(link.rel === "results") {
              $http.jsonp(link.href + "?callback=JSON_CALLBACK").
                success(function(results) {
                scope.$broadcast("results", results);
                resultsFuture.resolve(results);
              }).
                error(function(data, status) {
                scope.$broadcast("error", data);
                resultsFuture.reject("Failed to fetch results" + status);
              });
            }
          });
        }
      });
    }

    var __nonPoll = ["failed", "completed", "canceled"];
    var longPoll = function(url) {
      (function doPoll() {
        $http.get(url).success(function(data) {
          update("update", data);
          if(__nonPoll.indexOf(data.status) === -1) {
            doPoll();
          }
        });
      })();
    }

    var webSocket = function(url) {
      var socket = new WebSocket(url);

      socket.onmessage = function(event) {
        var data = angular.fromJson(event.data);
        if(__nonPoll.indexOf(data.status) !== -1) {
          socket.close();
        }
        update("update", data);
      };
    }

    $http.post(this.url, data)
    .success(function(data) {
      angular.forEach(data._links, function(link) {
        if(link.rel === "self") {
          $http.get(link.href).success(function(data) {
            update("update", data);
          });
        } else if (link.rel === "status") {
          var done = self.lastStatus && __nonPoll.indexOf(self.lastStatus.status) !== -1;
          if(!done) {
            if(hasWebsockets() && link.websocket) {
              webSocket(link.websocket);
            } else {
              longPoll(link.href);
            }
          }
        }
      });
    })
    .error(function(data, status) {
      scope.$broadcast("error", data);
      resultsFuture.reject("Failed to fetch results" + status);
    });
  }

  return {
    submit: function(method, data) { return new Task(method, data); }
  }
});
