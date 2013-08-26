'use strict';

window.clinicico = (function () {
  var domain = "localhost:3000/service";
  var wsuri = "ws://" + domain;

  var Task = function(method, payload) {
    var resultsPromise = when.defer();
    var self = this;
    this.results = resultsPromise.promise;

    var session = ab.connect(wsuri +  "/" + method + "/ws", function(session) {
      console.log("Connected to " + wsuri, session.sessionid());
      // Subscribe to updates
      session.subscribe("http://myapp/status#", function(topic, event) {
        resultsPromise.notify(event);
      });

      // Send-off RPC
      self.results = session.call("http://myapp/rpc#", payload).then(
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

    }, function(code, reason) {
      resultsPromise.reject(reason);
      console.log(code, reason);
    });
  }

  var clinicico = {
    submit: function (method, payload) {
      return new Task(method, payload);
    }
  };

  return clinicico;
}());
