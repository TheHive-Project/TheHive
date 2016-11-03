(function() {
    'use strict';
    angular.module('theHiveFilters').filter('shortDate', function() {
        return function(str) {
            if (angular.isString(str) && str.length > 0) {
                return moment(str, ['YYYYMMDDTHHmmZZ', 'DD-MM-YYYY HH:mm']).format(' MM/DD/YY H:mm');
            } else {
                return '';
            }

        };
    });
})();
