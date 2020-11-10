(function() {
    'use strict';
    angular.module('theHiveFilters').filter('customFieldValue', function() {
        return function(customField) {
            if(!customField) {
                return '';
            }

            switch(customField.type) {
                case 'date':
                    return moment(customField.value).format('MM/DD/YY H:mm');
                default:
                    return customField.value;
            }
        };
    });
})();
