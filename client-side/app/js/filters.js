'use strict';

/* Filters */

angular.module('cliniccio.filters', []).
  filter('interpolate', ['version', function(version) {
    return function(text) {
      return String(text).replace(/\%VERSION\%/mg, version);
    }
  }])

angular.module('cliniccio.filters', []).
  filter('precision', function() { 
    return function(number, decimals) { 
      return number.toFixed(decimals)
    };
});
