'use strict';

/* Services */
angular.module('clinicico', []).
  value('version', '0.1').
  factory('clinicico.tasks', function ($q, $rootScope, $http) {
    var config = {};
    config.baseUrl = "http://localhost:3000/tasks/";

    var Task = function(method, data) {
      var scope = $rootScope.$new(true);
      var self = this;
      this.method = method;
      this.url = config.baseUrl + method;
      this.data = data;
      var resultsFuture = $q.defer();
      this.results = resultsFuture.promise;

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

      function update(eventName, data) {
        scope.safeApply(function() {
          scope.$broadcast(eventName, data);
        });
        if(data.results) {
          angular.forEach(data._links, function(link) {
            if(link.rel === "results") {
              $http.get(link.href).
                success(function(results) {
                    scope.$broadcast("results", results);
                    resultsFuture.resolve(results);
                  }).
                error(function(data, status) {
                  console.log(data);
                  scope.$broadcast("error", data);
                  resultsFuture.reject("Failed to fetch results" + status);
              });
            }
          });
        }
      }

      var longPoll = function(url) {
        var __nonPoll = ["failed", "completed", "canceled"];
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
          var data = JSON.parse(event.data);
          update("update", data);
        };
      }

      $http.post(this.url, data).success(function(data) {
        scope.$broadcast("update", data);
        angular.forEach(data._links, function(link) {
          if(link.rel === "status") {
            if(window.WebSocket && link.websocket) {
              webSocket(link.websocket);
            } else {
              longPoll(link.href);
            }
          }
        });
      });
    }

    return {
      submit: function(method, data) { return new Task(method, data); }
    }
  });
