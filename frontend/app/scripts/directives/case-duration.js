(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('caseDuration', function() {
        return {
            restrict: 'E',
            scope: {
                start: '=',
                end: '=',
                icon: '@',
                indicator: '='
            },
            templateUrl: 'views/directives/case-duration.html'
        };
    });
})();
