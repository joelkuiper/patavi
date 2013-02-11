'use strict';

/* Filters */
angular.module('clinicico.filters', []).
  filter('precision', function() { 
    return function(number, decimals) { 
      return number.toFixed(decimals)
    };
}).
  filter('groupUs', function() { 
    return function(array, group) { 
      return _.groupBy(array, function(x) { return x[group]; });
    }
});
