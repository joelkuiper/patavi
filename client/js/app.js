angular.module('example', ["clinicico"]);

function TaskCtrl($scope, tasks) {
  var slow = tasks.submit("http://localhost:3000/tasks/slow", {"foo":"bar"});
  slow.on("update", function(status) {
    $scope.status = status;
  });

  slow.results.then(function(results) {
    $scope.results = results;
  });

}
TaskCtrl.$inject = ['$scope', 'tasks'];
