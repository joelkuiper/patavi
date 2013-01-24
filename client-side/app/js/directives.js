'use strict';

/* Directives */


angular.module('cliniccio.directives', []).
  directive('appVersion', ['version', function(version) {
    return function(scope, elm, attrs) {
      elm.text(version);
    };
  }]);

angular.module('cliniccio.directives', []).
  directive('fineUploader', function() {
  return {
    restrict: 'A',
    require: '?ngModel',
    link: function($scope, element, attributes, ngModel) {
      var uploader = new qq.FineUploader({
        element: element[0],
        request: {
          endpoint: attributes.uploadDestination,
        },
        validation: {
          allowedExtensions: attributes.uploadExtensions.split(',')
        },
        text: {
            uploadButton: '<i class="icon-upload icon-white"></i> Upload File'
        },
        template: '<div class="qq-uploader">' +
                    '<pre class="qq-upload-drop-area"><span>{dragZoneText}</span></pre>' +
                    '<div class="qq-upload-button btn btn-info" style="width:auto;">{uploadButtonText}</div>' +
                    '<span class="qq-drop-processing"><span>{dropProcessingText}</span></span>' +
                    '<ul class="qq-upload-list" style="margin-top: 10px; text-align: center;"></ul>' +
                  '</div>',
        classes: {
          success: 'alert alert-success',
          fail: 'alert alert-error'
        },
        callbacks: {
          onComplete: function(id, fileName, responseJSON) {
           //duplicate the previous view value.
           var copy = angular.copy(ngModel.$viewValue);

           //add the new objects
           copy.push(responseJSON);

           //update the model and run form validation.
           ngModel.$setViewValue(copy);

           //queue a digest.
           $scope.$apply();
          }}
      });
    }
  }});
