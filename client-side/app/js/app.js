'use strict';

// Declare app level module which depends on filters, and services
angular.module('clinicico', ['clinicico.filters', 'clinicico.services', 'clinicico.directives', 'ui.bootstrap']).
config(['$routeProvider', function($routeProvider) {
	$routeProvider.when('/home', {
		templateUrl: 'partials/home.html',
		controller: HomeCtrl
	});
	$routeProvider.when('/about', {
		templateUrl: 'partials/about.html',
		controller: AboutCtrl
	});
	$routeProvider.when('/analyses', {
		templateUrl: 'partials/analyses.html',
		controller: AnalysesCtrl
	});
	$routeProvider.otherwise({
		redirectTo: '/analyses'
	});
}]).run(function($rootScope) {
	$rootScope.hello = function() { alert("foo"); }});

