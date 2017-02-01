(function () {
    'use strict';

    angular.module('theHiveDirectives')
        .directive('autofocus', function ($timeout) {
            return {
                restrict: 'A',
                link: function ($scope, $element, attr) {
                                        
                    $scope.$on(attr.autofocus, function() {
                        console.log('Event received = ' + attr.autofocus);
                        $timeout(function() {
                            $element[0].focus();
                        }, 0);
                        
                    });                                        
                }
            }
        });
})();
