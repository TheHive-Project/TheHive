(function() {
    'use strict';
    angular.module('theHiveFilters').filter('getField', function() {
        return function(obj, param) {
            if (angular.isDefined(obj)) {
                return obj[param];
            } else {
                return '';
            }
        };
    });
})();
