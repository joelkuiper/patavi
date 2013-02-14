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
		this.results = {};
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
	var pop = function(obj) {
		for (var key in obj) {
			if (!Object.hasOwnProperty.call(obj, key)) continue;
			var result = obj[key];
			// If the property can't be deleted fail with an error.
			if (!delete obj[key]) {
				throw new Error();
			}
			return result;
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
		get: function(id) {
			return _.find(this.analyses, function(a) {
				return a.id === id;
			});
		},

		addResults: function(id, results) {
			var r = angular.copy(results.results);
			_.each(_.range(3), function() {
				pop(r.consistency.results) // remove the first network, description and treatments
			});
			this.get(id).results = r;
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
		__polling: false,
		__nonPoll: ["completed", "failed", "canceled"],

		isReady: function() { 
			var self = this;
			return (_.filter(this.jobs, function(j) { 
				return self.__nonPoll.indexOf(j.data.status) == - 1
			}).length === 0);
		},

		query: function() {
			return this.jobs;
		},

		startPoll: function() {
			var jobs = this.jobs;
			var self = this;
			(function tick() {
				_.each(jobs, function(job) {
					if (self.__nonPoll.indexOf(job.data.status) == - 1) {
						$http.get(job.data.job).success(function(data) {
							updateJob(job.data, data);
						});
					} else {
						if (!job.executed && job.data.status === "completed") {
							$http.get(job.data.results).success(function(data) {
								job.results = data;
								$rootScope.$broadcast(job.broadcast, job);
							});
						}
						job.executed = true;
					}
				});
				$timeout(tick, 500);
			})();
		},

		add: function(job) {
			if (!this.__polling) {
				this.startPoll();
				this.__polling = true;
			}
			this.jobs.push(job);
			$rootScope.$broadCast("jobAdded");
		},

		get: function(id) {
			return _.find(this.jobs, function(job) {
				return job.id === id;
			});
		},
	}
	return Jobs;
}]);

