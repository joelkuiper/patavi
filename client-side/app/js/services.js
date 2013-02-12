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
		this.treatments = [{
			id: "foo",
			description: "bar"
		}];
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
		get: function(id) {
			_.find(this.query, function(a) {
				return a.id == id
			});
		}
	}

	return Analyses;
});

