'use strict';

/* Services */
angular.module('clinicico', []).
  value('version', '0.1').
  factory('tasks', function ($rootScope, $http) {
    var Task = function(location, data) {
      this.location = location;
      this.data = data;
      var self = this;

      $http.post(location, data).success(function(data) {
        console.log(data);
      });
    }

    return {
      submit: function(location, data) { return new Task(location, data); }
    }
    //var socket = io.connect();
    //return {
      //on: function (eventName, callback) {
        //socket.on(eventName, function () {
          //var args = arguments;
          //$rootScope.$apply(function () {
            //callback.apply(socket, args);
          //});
        //});
      //},
      //emit: function (eventName, data, callback) {
        //socket.emit(eventName, data, function () {
          //var args = arguments;
          //$rootScope.$apply(function () {
            //if (callback) {
              //callback.apply(socket, args);
            //}
          //});
        //})
      //}
    //};
  });
