(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('pageSizer', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/directives/page-sizer.html',
                scope: {
                    collection: '=',
                    sizes: '=?',
                    cls: '@?'
                },
                link: function(scope) {
                    if (!scope.sizes) {
                        scope.sizes = [10, 25, 50, 100, 250, 500];
                    }
                }
            };
        });
})();
