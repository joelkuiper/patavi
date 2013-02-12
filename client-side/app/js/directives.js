'use strict';

/* Directives */

angular.module('clinicico.directives', []).
directive("async", function() {
	return {
		restrict: "A",
		require: '?ngModel',
		link: function(scope, element, attributes, ngModel) {

			var progressHandling = function progressHandlingFunction(e) {
				if (e.lengthComputable) {
					$('progress').attr({
						value: e.loaded,
						max: e.total
					});
				}
			}
			element.bind("submit", function() {
				var formData = new FormData($(element)[0]);
				$.ajax({
					url: attributes.async,
					type: 'POST',
					data: formData,
					success: function(responseJSON, textStatus, jqXHR) {
						//duplicate the previous view value.
						var copy = angular.copy(ngModel.$viewValue);

						//add the new objects
						copy.push(responseJSON);

						//update the model and run form validation.
						ngModel.$setViewValue(copy);

						//queue a digest.
						scope.$apply();
					},
					cache: false,
					contentType: false,
					processData: false
				});
			});
		}
	};
}).
directive('analysis', function() {
	return {
		restrict: 'E',
    controller: 'AnalysisCtrl',
		scope: {
			analysis: '='
		},
		replace: true,
		templateUrl: 'partials/analysis-detail.html',
	}
});

