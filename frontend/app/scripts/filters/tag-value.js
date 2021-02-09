(function() {
    'use strict';

    angular.module('theHiveFilters').filter('tagValue', function () {
        return function (tag) {
            if (!tag) {
                return '';
            }

            return _.without([
                tag.namespace,
                ':',
                tag.predicate,
                tag.value ? ("=\"" + tag.value + "\"") : null
            ], null).join('');
        };
    });
})();
