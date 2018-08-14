(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('autoFocus', function($timeout) {
        return {
            restrict: 'A',
            link: function(scope, element, attrs) {
                if (attrs.autoFocus) {
                    scope.$on(attrs.autoFocus, function() {
                        $timeout(function() {
                            element[0].focus();
                        });
                    });
                } else {
                    $timeout(function() {
                        element[0].focus();
                    });
                }
            }
        };
    });
})();
