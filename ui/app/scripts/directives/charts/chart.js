(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('chart', function() {
        return {
            restrict: 'E',
            scope: {
                type: '@',
                autoload: '=',
                options: '=',
                refreshOn: '@'
            },
            templateUrl: 'views/directives/charts/chart.html'
        };
    });

})();
