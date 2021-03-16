(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('alertDuration', function() {
        return {
            restrict: 'E',
            scope: {
                start: '=',
                end: '=',
                icon: '@',
                indicator: '='
            },
            templateUrl: 'views/directives/alert-duration.html'
        };
    });
})();
