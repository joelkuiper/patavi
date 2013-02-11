'use strict';

/* Controllers */

function JobCtrl($scope, $http, $timeout) {
   
  var getUUID = function(path) { 
    var parser = document.createElement('a');
    parser.href = path;
    return parser.pathname.split("/").pop();
  }
  $scope.tooltip = {cancel:"Cancel queued job"}
  $scope.jobs = [];
  $http.get("/api/job/session").success(function(data) { 
    $scope.jobs = data;
    $scope.jobs.forEach(function(job) {
      job.uuid = getUUID(job.results); 
    });
  });

  var pushToScope = function(job, data) {
    for (var field in data) { 
      job[field] = data[field];
    }
    job.uuid = getUUID(job.results);
  }

  $scope.cancel = function(job) { 
    $http({method:'DELETE', url:job}).success(function(status) { 
      pushToScope(job, status); 
    });
  }

  // Poll the status of each listed job every 3 seconds
  var poll = function() {
    (function tick() {
      $scope.jobs.forEach(function(job) { 
        var nonPoll = ["completed", "failed", "canceled"];
        if(nonPoll.indexOf(job.status) == -1) {
          $http.get(job.job).success(function(data) { 
           pushToScope(job, data);
          });
        }});
      $timeout(tick, 3000);
    })();
  }
  poll();

}

JobCtrl.$inject = ['$scope', '$http']

function ResultCtrl($scope, Result, $routeParams) {
  $scope.uuid = $routeParams.uuid;
  $scope.results = {};
  $scope.network = {};
 
  var pop = function(obj) {
    for (var key in obj) {
      if (!Object.hasOwnProperty.call(obj, key)) continue;
      var result = obj[key];
      // If the property can't be deleted fail with an error.
      if (!delete obj[key]) { throw new Error(); }
      return result;
    } 
  }
  $scope.result = Result.get({uuid: $scope.uuid}, function(result) { 

    $scope.network = pop(result.results.consistency.results);
    $scope.network.treatments = pop(result.results.consistency.results);
    $scope.network.description = pop(result.results.consistency.results);
    $scope.results = result;
  });

}
ResultCtrl.$inject = ['$scope', 'Result', '$routeParams']

function AnalysisCtrl($scope, Analyses, $routeParams) {
  $scope.analyses = [];
  $scope.addEmpty = function() {
    $scope.analyses.push({title:"Untitled analysis", 
                          content: {data: [],
                                    treatments: [{id: "foo", description: "bar"}],
                                    description: ""}});
  }
}
AnalysisCtrl.$inject = ['$scope', '$http']

function AddStudyCtrl($scope) { 
  var studyProto = {treatments: {}, id: ""}
  $scope.newStudy = angular.copy(studyProto);
  $scope.open = function () {
    $scope.shouldBeOpen = true;
  };

  $scope.addStudy = function(analysis) { 
    var copy = angular.copy(analysis);
    var studyData = _.map($scope.newStudy.treatments, function(included, id) { 
      if(included) {
        return {study: $scope.newStudy.id, treatment: id};
      }
    });
    analysis.data = _.union(studyData, copy.data);

    $scope.newStudy = angular.copy(studyProto);
    $scope.shouldBeOpen = false;
  }

  $scope.close = function () {
    $scope.shouldBeOpen = false;
    $scope.newStudy = angular.copy(studyProto);
  };
}
AddStudyCtrl.$inject = ['$scope']
