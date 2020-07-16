(function() {
    'use strict';
    angular.module('theHiveFilters').filter('shortDate', function() {
        return function(str) {
            var format = 'MM/DD/YY H:mm';
            if (angular.isString(str) && str.length > 0) {
                return moment(str, ['YYYYMMDDTHHmmZZ', 'DD-MM-YYYY HH:mm']).format(format);
            } else if (angular.isNumber(str)) {
                return moment(str).format(format);
            } else {
                return '';
            }

        };
    });
})();
