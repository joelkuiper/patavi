'use strict';

/* Controllers */

function JobCtrl($scope, $http, $timeout) {
  
  var getUUID = function(path) { 
    var parser = document.createElement('a');
    parser.href = path;
    return parser.pathname.split("/").pop();
  }

  $scope.jobs = [];
  $http.get("/api/job/session").success(function(data) { 
    $scope.jobs = data;
    $scope.jobs.forEach(function(job) {
      job.uuid = getUUID(job.results); 
    });
  });
  
  var poll = function() {
    (function tick() {
      $scope.jobs.forEach(function(job) { 
        if(job.status !== "completed" && job.status !== "failed") {
          $http.get(job.job).success(function(data) { 
            for (var field in data) { 
              job[field] = data[field];
            }
            job.uuid = getUUID(job.results);
          });
        }});
      $timeout(tick, 3000);
    })();
  }
  poll();

}

JobCtrl.$inject = ['$scope', '$http', '$timeout']

function ResultCtrl($scope, Result, $routeParams) {
  $scope.uuid = $routeParams.uuid;
  $scope.network = {};
  $scope.colDefs = [];  
  $scope.consistency = {};  
 
  $scope.result = Result.get({uuid: $scope.uuid}, function(result) { 
    $scope.network = result.network;
    $scope.consistency = result.results.consistency;
    console.log(result);
  });

  $scope.networkGrid = {data: 'network.data',
    displayFooter: false,
    canSelectRows: false,
    displaySelectionCheckbox: false,
    columnDefs: 'colDefs'};

  var colDefs = function(x) { 
    var colDefs = [];
    if(x) { 
      for (var key in x[0]) { // First element (i.e. row) should have all the definitions
        var el = x[0][key];
        var floatFilter = (typeof el === 'number' && !(el % 1 == 0)) ? "precision:3" : null;
        colDefs.push({"field": key, "cellFilter": (floatFilter || "")});
      }
    }
    return colDefs;
  };

  $scope.$watch('network', function(newVal, oldVal) {
    if(newVal.data) { 
      $scope.colDefs = colDefs($scope.network.data);
    }
  });
}
ResultCtrl.$inject = ['$scope', 'Result', '$routeParams']
