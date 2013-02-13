'use strict';

/* Directives */

angular.module('clinicico.directives', []).
directive("asyncJob", ['Jobs', function(Jobs) {
	return {
		restrict: "A",
		require: '?ngModel',
		link: function(scope, element, attributes, ngModel) {
			element.bind("submit", function() {
				var formData = new FormData($(element)[0]);
				$.ajax({
					url: attributes.asyncJob,
					type: 'POST',
					data: formData,
					success: function(responseJSON, textStatus, jqXHR) {
						Jobs.add({
							data: responseJSON,
							type: attributes.jobType,
							broadcast: attributes.broadcast
						});
					},
					cache: false,
					contentType: attributes.enctype || false,
					processData: false
				});
			});
		}
	};
}]).
directive('analysis', function() {
	return {
		restrict: 'E',
		controller: 'AnalysisCtrl',
		scope: {
			analysis: '='
		},
		replace: true,
		templateUrl: 'partials/analysis.html',
	}
}).
directive('network', function() {
	return {
		restrict: 'E',
		controller: 'AnalysisCtrl',
		scope: {
			analysis: '='
		},
		replace: true,
		templateUrl: 'partials/analysis-detail.html',
	}
}).
directive('result', function() {
	return {
		restrict: 'E',
		controller: 'ResultCtrl',
		scope: {
			analysis: '='
		},
		replace: true,
		templateUrl: 'partials/result-detail.html',
	}
}).
directive('regexValidate', function() {
	// From http://www.benlesh.com/2012/12/angular-js-custom-validation-via.html
	return {
		restrict: 'A',
		require: 'ngModel',
		link: function(scope, elem, attr, ctrl) {

			var flags = attr.regexValidateFlags || '';
			var regex = new RegExp(attr.regexValidate, flags);

			ctrl.$setValidity('regexValidate', regex.test(ctrl.$viewValue));

			ctrl.$parsers.unshift(function(value) {
				ctrl.$setValidity('regexValidate', regex.test(value));
				return value;
			});
		}
	};
}).
directive('floatValidate', function() {
	return {
		restrict: 'A',
		require: 'ngModel',
		link: function(scope, elem, attr, ctrl) {

			function isInt(n) {
				return typeof n === 'number' && parseFloat(n) == parseInt(n, 10) && ! isNaN(n);
			}
			ctrl.$setValidity('floatValidate', isInt(ctrl.$viewValue));

			ctrl.$parsers.unshift(function(value) {
				ctrl.$setValidity('floatValidate', isInt(value));
				return value;
			});
		}
	};
});

