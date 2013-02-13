'use strict';

/* Services */

angular.module('clinicico.services', ['ngResource']).
factory('Result', function($resource) {
	return $resource('api/result/:uuid', {},
	{});
}).
factory('Analyses', function() {
	var createUUID = function() {
		return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
			var r = Math.random() * 16 | 0,
			v = c == 'x' ? r: (r & 0x3 | 0x8);
			return v.toString(16);
		});
	}

	function Analysis() {
		this.id = createUUID();
		this.title = "Untitled analysis";
		this.description = "";
		this.treatments = [];
		this.data = [];
		var self = this;
		this.addStudy = function(study) {
			var newData = _.map(study.treatments, function(included, id) {
				if (included) {
					return {
						study: study.id,
						treatment: id
					};
				}
			});
			self.data = _.union(self.data, newData);
		}

		this.deleteStudy = function(study) {
			var newStudies = _.reject(self.data, function(s) {
				console.log(s);
				console.log(study);
				return s.study === study;
			});
			console.log(newStudies);
			self.data = newStudies;
		}

		this.addTreatment = function(treatment) {
			self.treatments.push(treatment);
		}
	}

	var Analyses = {
		analyses: [],

		create: function() {
			this.analyses.push(new Analysis());
		},
		query: function() {
			return this.analyses;
		},

		fromGeMTC: function(data) {
			var newAnalysis = new Analysis();
			_.each(data, function(d) {
				newAnalysis[d.name] = d.data;
			});
			this.analyses.push(newAnalysis);
		}
	}

	return Analyses;
}).
factory("Jobs", ['$rootScope', '$http', '$timeout', function($rootScope, $http, $timeout) {
	var getUUID = function(path) {
		var parser = document.createElement('a');
		parser.href = path;
		return parser.pathname.split("/").pop();
	}

	var updateJob = function(job, data) {
		for (var field in data) {
			job[field] = data[field];
		}
		job.uuid = getUUID(job.results);
	}

	var Jobs = {
		jobs: [],
		polling: false,

		query: function() {
			return this.jobs;
		},
		startPoll: function() {
			var jobs = this.jobs;
			(function tick() {
				var nonPoll = ["completed", "failed", "canceled"];
				_.each(jobs, function(job) {
					if (nonPoll.indexOf(job.data.status) == - 1) {
						$http.get(job.data.job).success(function(data) {
							updateJob(job.data, data);
						});
					} else {
						if (!job.executed && job.data.status === "completed") {
							// Get results 
							$http.get(job.data.results).success(function(data) {
								job.results = data;
								$rootScope.$broadcast(job.broadcast, job);
							});
							job.executed = true;
						}
					}
				});
				$timeout(tick, 500);
			})();
		},
		add: function(job) {
			if (!this.polling) {
				this.startPoll();
			}
			this.jobs.push(job);
		},
		get: function(id) {
			_.find(this.jobs, function(job) {
				return job.id === id;
			});
		},
	}
	return Jobs;
}]);

