(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('chart', function() {
        return {
            restrict: 'E',
            scope: {
                type: '@',
                autoload: '=',
                options: '=',
                refreshOn: '@',
                mode: '@'
            },
            templateUrl: 'views/directives/charts/chart.html'
        };
    });

})();
