'use strict';

/* Controllers */
function AnalysesCtrl($scope, $timeout, $http){
  $scope.job = {};
  $scope.results = {};
  $scope.network = {};
  $scope.colDefs = [];

  $scope.hasNetwork = function() { 
    return !($scope.network.data === undefined)
  };

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

  $scope.networkGrid = {data: 'network.data',
    displayFooter: false,
    canSelectRows: false,
    displaySelectionCheckbox: false,
    columnDefs: 'colDefs'};

  $scope.$watch('job', function(newVal, oldVal) { 
    if(newVal.job) { 
      (function poll(){
        setTimeout(function() {
          $http.get(newVal.job)
          .success(function(data, status) {
            if(status === 204) {
              poll()
            } else {
              $scope.network = data.network;
              $scope.results = data.results.consistency;
            }
          })
        .error(function(data, status) { 
          console.log("Error" + data + " " + status);
        });
        }, 1000);
      })();
    }
  });
}
AnalysesCtrl.$inject = ['$scope', '$timeout', '$http']

