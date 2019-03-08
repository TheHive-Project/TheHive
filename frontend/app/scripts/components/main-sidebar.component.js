(function() {
    'use strict';
    angular.module('theHiveControllers')
        .directive('mainSidebar', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/components/main-sidebar.component.html'
            };
        });
})();
