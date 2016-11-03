(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('psearch', function() {
            return {
                'restrict': 'E',
                'templateUrl': 'views/directives/psearch.html',
                'scope': {
                    'control': '='
                }
            };
        });
})();
