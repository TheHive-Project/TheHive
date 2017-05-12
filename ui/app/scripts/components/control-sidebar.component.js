(function() {
    'use strict';
    angular.module('theHiveControllers')
        .directive('controlSidebar', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/components/control-sidebar.component.html'
            };
        });
})();
