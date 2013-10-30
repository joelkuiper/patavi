'use strict';

angular.module('example', []);

function TaskCtrl($scope) {
  $scope.method = "slow";
  $scope.input = "{}";

  $scope.submit = function(method, input) {
    var task = patavi.submit(method, angular.fromJson(input));
    $scope.error = null;
    $scope.status = null;
    $scope.results = null;

    var handlerFactory = function(type) {
      return function(x) {
        $scope[type] = x;
        $scope.$apply();
      };
    };

    var progressHandler = handlerFactory("status");
    var errorHandler = handlerFactory("error");
    var successHandler = handlerFactory("results");

    task.results.then(successHandler, errorHandler, progressHandler);
  };
}
TaskCtrl.$inject = ['$scope'];
