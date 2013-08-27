'use strict';

window.patavi = (function () {
  var config = window.patavi || {};
  var WS_URI = typeof config['WS_URI'] !== 'undefined' ? config['WS_URI'] : "ws://localhost:3000/ws";
  var BASE_URI = typeof config['BASE_URI'] !== 'undefined' ? config['BASE_URI'] : "http://api.patavi.com/";

  var Task = function(method, payload) {
    var resultsPromise = when.defer();
    var self = this;
    this.results = resultsPromise.promise;

    var session = ab.connect(WS_URI, function(session) {
      console.log("Connected to " + WS_URI, session.sessionid());
      // Subscribe to updates
      session.subscribe(BASE_URI + "status#", function(topic, event) {
        resultsPromise.notify(event);
      });

      // Send-off RPC
      self.results = session.call(BASE_URI + "rpc#", method, payload).then(
        function(result) {
          resultsPromise.resolve(result);
          session.close();
        },
        function(reason, code) {
          console.log("error", code, reason);
          resultsPromise.reject(reason);
          session.close();
        }
      );

    }, function(reason, code) {
      resultsPromise.reject(reason);
      console.log(code, reason);
    });
  }

  var patavi = {
    submit: function (method, payload) {
      return new Task(method, payload);
    }
  };

  return patavi;
}());
