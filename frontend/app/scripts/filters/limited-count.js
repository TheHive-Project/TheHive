(function () {
    'use strict';

    angular.module('theHiveFilters')
        .filter('limitedCount', function () {
            return function (count) {
                if (isNaN(count))
                    return 0

                if (count < 0)
                    return (-1 * count) + '+';

                return count;
            };
        });
})();
