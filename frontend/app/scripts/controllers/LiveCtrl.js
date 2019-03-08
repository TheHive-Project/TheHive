(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('LiveCtrl', function($rootScope, $scope, $window, StreamSrv) {
            StreamSrv.init();
            $rootScope.hideStatusBar = true;
            if ($window.opener) {
                $scope.targetWindow = $window.opener;
            } else {
                $scope.targetWindow = '_blank';
            }
        });
})();
