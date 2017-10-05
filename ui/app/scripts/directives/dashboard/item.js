(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('dashboardItem', function() {
            return {
                restrict: 'E',
                scope: {
                    type: '@',
                    autoload: '=',
                    options: '=',
                    refreshOn: '@',
                    mode: '@'
                },
                templateUrl: 'views/directives/dashboard/item.html',
                link: function(scope, element) {

                }
            };
        });
})();
