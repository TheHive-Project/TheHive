(function() {
    'use strict';
    angular.module('theHiveFilters').filter('percentage', function($filter) {
        return function(input, decimals) {
            return $filter('number')(input * 100, decimals) + '%';
        };
    });
})();
