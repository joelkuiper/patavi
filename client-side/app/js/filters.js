'use strict';

/* Filters */
angular.module('cliniccio.filters', []).
  filter('precision', function() { 
    return function(number, decimals) { 
      return number.toFixed(decimals)
    };
});
