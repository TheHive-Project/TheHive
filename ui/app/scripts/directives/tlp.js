(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('tlp', function(UtilsSrv) {
        return {
            restrict: 'E',
            scope: {
                'value': '=',
                'format': '=?',
                'onUpdate': '&'
            },
            'link': UtilsSrv.updatableLink,
            'templateUrl': 'views/directives/tlp.html',
            controller: function($scope) {
                if ($scope.value === null || $scope.value === undefined) {
                    $scope.value = 2;
                }
            }
        };
    });

})();
