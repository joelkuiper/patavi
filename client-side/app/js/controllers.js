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

function JobCtrl($scope, $http, $timeout, Analyses) {
	$scope.jobs = [];
	$scope.results = [];
	$scope.allowSubmission = true;

	var getUUID = function(path) {
		var parser = document.createElement('a');
		parser.href = path;
		return parser.pathname.split("/").pop();
	}

	$scope.cancel = function(job) {
		$http({
			method: 'DELETE',
			url: job
		}).success(function(status) {
			pushToScope(job, status);
		});
	}

	var pushToScope = function(job, data) {
		for (var field in data) {
			job[field] = data[field];
		}
		job.uuid = getUUID(job.results);
	}
	$scope.fromGeMTC = function() {
		Analyses.fromGeMTC($scope.results.pop().results.network.results);
	};

	var isCompleted = function() { 
		var statuses = _.pluck(_.pluck($scope.jobs, 'job'), 'status');
		return !_.some(statuses, function(x) { return x == 'running' || x == 'pending' }); 
	}

	var setAllowSubmission = function() { 
		$scope.allowSubmission = isCompleted();
	}

	$scope.$watch('jobs', function() { 
		setAllowSubmission();
	});

	// Poll the status of each listed job every 3 seconds
	var poll = function() { (function tick() {
				var nonPoll = ["completed", "failed", "canceled"];
			_.each($scope.jobs, function(job) {
				if (nonPoll.indexOf(job.job.status) == - 1) {
					$http.get(job.job.job).success(function(data) {
						pushToScope(job.job, data);
					});
				} else {
					if (!job.executed && job.job.status === "completed") {
						// Get results 
						$http.get(job.job.results).success(function(data) {
							$scope.results.push(data); 
							$scope.$eval(job.successFn);
						});
						job.executed = true;
					}
					setAllowSubmission();
				}
			});
			$timeout(tick, 500);
		})();
	}
	poll();
}

AnalysesCtrl.inject = ['$scope', '$http', '$timeout', 'Analyses']

