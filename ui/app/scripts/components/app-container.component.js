(function() {
    'use strict';
    angular.module('theHiveControllers')
        .directive('appContainer', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/components/app-container.component.html'
            };
        });
})();
