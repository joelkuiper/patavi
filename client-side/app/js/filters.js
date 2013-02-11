'use strict';

/* Filters */
angular.module('clinicico.filters', []).
  filter('precision', function() { 
    return function(number, decimals) { 
      return number.toFixed(decimals)
    };
});
