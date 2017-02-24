(function() {
    'use strict';
    angular.module('theHiveControllers')
        .directive('header', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/components/header.component.html'
            };
        });
})();
