(function () {
    'use strict';

    angular.module('theHiveDirectives')
        .directive('autofocus', function ($timeout) {
            return {
                restrict: 'A',
                link: function ($scope, $element, attr) {

                    $scope.$on(attr.autofocus, function() {
                        $timeout(function() {
                            $element[0].focus();
                        }, 0);

                    });
                }
            }
        });
})();
