(function() {
    'use strict';
    angular.module('theHiveFilters').filter('showDate', function() {
        return function(str, format) {
            var fmt = format || 'ddd, MMM Do, YYYY H:mm Z';

            if (angular.isString(str) && str.length > 0) {
                return moment(str, ['YYYYMMDDTHHmmZZ', 'DD-MM-YYYY HH:mm']).format(fmt);
            } else if (angular.isNumber(str)) {
                return moment(str).format(fmt);
            } else {
                return '';
            }

        };
    });
})();
