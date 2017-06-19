(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('miniReportList', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/directives/mini-report-list.html',
                scope: {
                    reports: '='
                },
                link: function(scope) {
                    scope.taxonomies = [];

                    scope.$watch('reports', function(data) {
                        var keys = _.keys(data);
                        var taxonomies = [];

                        _.each(keys, function(key) {
                            taxonomies = taxonomies.concat(data[key].taxonomies || []);
                        });

                        scope.taxonomies = taxonomies;
                    });
                }
            };
        });
})();
