(function() {
    'use strict';
    angular.module('theHiveFilters').filter('offset', function($filter) {
        return function(input, start) {
            if(!input) return;
            start = parseInt(start, 10);
            return input.slice(start);
        };
    });
})();
