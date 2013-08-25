angular.module('example', []);

function TaskCtrl($scope) {
  $scope.method = "slow";
  $scope.input = "{}";

  $scope.submit = function(method, input) {
    var task = clinicico.submit(method, angular.fromJson(input));

    var progressHandler = function(progress) {
      $scope.$apply(function() {
        $scope.status = progress;
      });
    }

    var successHandler = function(results) {
      $scope.$apply(function() {
        $scope.results = results;
      });
    }

    var errorHandler = function(error) {
      $scope.$apply(function() {
        $scope.results = error;
      });
    }

    task.results.then(successHandler, errorHandler, progressHandler);
  }

}
TaskCtrl.$inject = ['$scope'];
