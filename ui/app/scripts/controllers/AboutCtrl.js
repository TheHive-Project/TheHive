/**
 * Controller for About TheHive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AboutCtrl',
        function($rootScope, $scope, $uibModalInstance, VersionSrv, NotificationSrv) {
            VersionSrv.get().then(function(response) {
                $scope.version = response.versions;
            }, function(data, status) {
                NotificationSrv.error('AboutCtrl', data, status);
            });

            $scope.close = function() {
                $uibModalInstance.close();
            };
        }
    );
})();
