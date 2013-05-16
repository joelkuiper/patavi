angular.module('example', ["clinicico"]);

function TaskCtrl($scope, tasks) {
  var slow = tasks.submit("http://localhost:3000/tasks/slow", {"foo":"bar"});
}
TaskCtrl.$inject = ['$scope', 'tasks'];
