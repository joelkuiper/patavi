'use strict';

/* Ctrls */

function AppCtrl($scope, $location) {
	$scope.isActive = function(route) {
		return route === $location.path();
	}
}
AppCtrl.$inject = ['$scope', '$location'];

function HomeCtrl() {}
function AboutCtrl() {}

function AnalysesCtrl($scope, Analyses) {
	$scope.analyses = Analyses.query();

	$scope.createNew = function() {
		Analyses.create();
	}
	$scope.fromGeMTC = function(network) {
		Analyses.fromGeMTC(network);
	};

  $scope.$on('networkUploaded', function(e, job) { 
    var network = job.results.results.network.results;
    $scope.fromGeMTC(network);
  });
}
AnalysesCtrl.inject = ['$scope', 'Analyses']

function AnalysisCtrl($scope, Analyses, $dialog) {
	$scope.delete = {};

	var dialogBase = {
		backdrop: true,
		keyboard: true,
		backdropClick: true,
	}

	$scope.$watch('analysis.data', function() {
		$scope.analysis.__groupedMeasurements = _.groupBy($scope.analysis.data, function(x) {
			return x['study'];
		});
	});

	$scope.editTreatments = function() {
		var treatmentDialogOpts = _.extend({
			templateUrl: 'partials/edit-treatments.html',
			controller: 'TreatmentCtrl',
			treatments: $scope.analysis.treatments
		},
		dialogBase);

		var d = $dialog.dialog(treatmentDialogOpts)
		d.open().then(function(treatments) {
			if (treatments) {
				$scope.analysis.treatments = treatments;
			}
		});
	}

	$scope.deleteStudies = function(studies) {
		_.each(_.pairs(studies), function(study) {
			if (study[1]) {
				$scope.analysis.deleteStudy(study[0]);
			}
		});
	}

	$scope.createStudy = function() {
		var studyDialogOpts = _.extend({
			templateUrl: 'partials/add-study.html',
			controller: 'StudyCtrl',
			treatments: $scope.analysis.treatments
		},
		dialogBase);
		var d = $dialog.dialog(studyDialogOpts);
		d.open().then(function(result) {
			if (result) {
				$scope.analysis.addStudy(result);
			}
		});
	};
}
AnalysesCtrl.inject = ['$scope', 'Analyses', '$dialog']

function StudyCtrl($scope, dialog) {
	$scope.treatments = dialog.options.treatments;
	$scope.result = {
		id: "",
		treatments: {}
	};
	$scope.close = function(result) {
		dialog.close(result);
	};
}

function TreatmentCtrl($scope, dialog) {
	var copy = angular.copy(dialog.options.treatments);
	$scope.treatments = dialog.options.treatments;
	$scope.close = function(treatments) {
		dialog.close(treatments || copy);
	};

	$scope.remove = function(treatment) {
		var lst = _.reject($scope.treatments, function(t) {
			return t === treatment
		});
		$scope.treatments = lst;
	}

	$scope.add = function() {
		$scope.treatments.push({
			id: "",
			description: ""
		})
	}
}
