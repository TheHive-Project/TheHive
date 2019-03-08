(function() {
    'use strict';
    angular.module('theHiveFilters').filter('hashType', function() {
        return function(value) {
            if(!value) {
                return '';
            }

            if(value.length === 64) {
                return '**SHA256:** ' + value;
            } else if (value.length === 40) {
                return '**SHA1:** ' + value;
            } else if(value.length === 32) {
                return '**MD5:** ' + value;
            }

            return value;
        };
    });
})();
