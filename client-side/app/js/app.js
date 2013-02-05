'use strict';


// Declare app level module which depends on filters, and services
angular.module('clinicico', ['clinicico.filters', 'clinicico.services', 'clinicico.directives', 'ngGrid', '$strap.directives']).
  config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/jobs', {templateUrl: 'partials/jobs.html', controller: JobCtrl});
    $routeProvider.when('/result/:uuid', {templateUrl: 'partials/result.html', controller: ResultCtrl});
    $routeProvider.otherwise({redirectTo: '/jobs'});
  }]);

