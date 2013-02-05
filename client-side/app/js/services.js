'use strict';

/* Services */


// Demonstrate how to register services
// In this case it is a simple value service.
angular.module('clinicico.services', ['ngResource']).
factory('Result', function($resource){
  return $resource('api/result/:uuid', {}, {
  });
});

