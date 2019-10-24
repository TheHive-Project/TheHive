(function() {
    'use strict';

    angular.module('theHiveFilters').filter('filterValue', function() {
        return function(value) {
            if (angular.isArray(value)) {
                return _.pluck(value, 'text').join(', ');
            } else if(angular.isObject(value) && value.from !== undefined && value.to !== undefined) {
                var result = [];
                if(value.from !== null) {
                    result.push('From: ' + moment(value.from).hour(0).minutes(0).seconds(0).format('MM/DD/YY HH:mm'));
                }

                if(value.to !== null) {
                    result.push('To: ' + moment(value.to).hour(23).minutes(59).seconds(59).format('MM/DD/YY HH:mm'));
                }

                return result.join(', ');
            } else if(angular.isObject(value) && value.list !== undefined) {
                return _.pluck(value.list, 'label').join(', ');
            }

            return value || 'Any';
        };
    });

})();
