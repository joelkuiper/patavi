'use strict';


// Declare app level module which depends on filters, and services
angular.module('cliniccio', ['cliniccio.filters', 'cliniccio.services', 'cliniccio.directives', '$strap.directives']).
  config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/analysis', {templateUrl: 'partials/analysis.html', controller: AnalysesCtrl});
    //$routeProvider.when('/view2', {templateUrl: 'partials/partial2.html', controller: MyCtrl2});
    $routeProvider.otherwise({redirectTo: '/analysis'});
  }]);

