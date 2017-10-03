(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('donut', function() {
            return {
                scope: {
                    component: '='
                },
                templateUrl: 'views/directives/dashboard/donut.html',
                link: function(scope, element) {

                }
            };
        });
})();
