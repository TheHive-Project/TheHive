(function() {
    'use strict';

    angular.module('theHiveFilters').filter('flattern', function() {
        var flattern = function(obj, path, result) {
            var key,
                val,
                _path;
            path = path || [];
            result = result || {};
            for (key in obj) {
                val = obj[key];
                _path = path.concat([key]);
                if (val instanceof Object) {
                    flattern(val, _path, result);
                } else {
                    result[_path.join('.')] = val;
                }
            }

            return result;
        };

        return flattern;
    });
})();
