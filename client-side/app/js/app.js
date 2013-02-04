'use strict';


// Declare app level module which depends on filters, and services
angular.module('cliniccio', ['cliniccio.filters', 'cliniccio.services', 'cliniccio.directives', 'ngGrid', '$strap.directives']).
  config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/analysis', {templateUrl: 'partials/analysis.html', controller: AnalysesCtrl});
    $routeProvider.otherwise({redirectTo: '/analysis'});
  }]);

