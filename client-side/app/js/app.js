'use strict';


// Declare app level module which depends on filters, and services
angular.module('clinicico', ['clinicico.filters', 'clinicico.services', 'clinicico.directives', 'ui.bootstrap']).
  config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/jobs', {templateUrl: 'partials/jobs.html', controller: JobCtrl});
    $routeProvider.when('/result/:uuid', {templateUrl: 'partials/result.html', controller: ResultCtrl});
    $routeProvider.when('/analyses', {templateUrl: 'partials/analyses.html', controller: AnalysisCtrl});
    $routeProvider.otherwise({redirectTo: '/analyses'});
  }]);

