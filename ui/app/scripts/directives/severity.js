(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('severity', function(UtilsSrv) {
        return {
            restrict: 'E',
            scope: {
                value: '=',
                active: '=?',
                onUpdate: '&'
            },
            templateUrl: 'views/directives/severity.html',
            link: UtilsSrv.updatableLink,
            controller: function($scope) {
                if ($scope.value === undefined || $scope.value === null || $scope.value === '') {
                    $scope.value = 2;
                }
            }
        };
    });
})();
