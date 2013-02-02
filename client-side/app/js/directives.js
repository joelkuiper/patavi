'use strict';

/* Directives */

angular.module('cliniccio.directives', []).
directive("async", function() { 
  return { 
    restrict: "A",
    require: '?ngModel',
    link: function(scope, element, attributes, ngModel) { 

      var progressHandling = function progressHandlingFunction(e){
        if(e.lengthComputable){
          $('progress').attr({value:e.loaded,max:e.total});
        }
      } 
      element.bind("submit", function() { 
        var formData = new FormData($(element)[0]);
        $.ajax({ 
          url: attributes.async, 
          type: 'POST',
          data: formData, 
          success: function(data, textStatus, jqXHR) { 
            ngModel.$setViewValue(data); 
            scope.$apply();
          },
          cache: false,
          contentType: false,
          processData: false});
      });
    }
  };
});
