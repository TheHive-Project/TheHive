(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('responderActions', function(UtilsSrv) {
        return {
            restrict: 'E',
            replace: true,
            scope: {
                actions: '=',
                header: '@'
            },
            templateUrl: 'views/directives/responder-actions.html',
            link: function(scope, el) {
              console.log(scope);
            }
        };
    });
})();
