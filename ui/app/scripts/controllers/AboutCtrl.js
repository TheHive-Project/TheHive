/**
 * Controller for About TheHive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AboutCtrl',
        function($rootScope, $scope, $uibModalInstance, VersionSrv, NotificationSrv) {
            VersionSrv.get().then(function(response) {
                console.log(response);
                $scope.version = response.versions;
                $scope.connectors = response.connectors;
            }, function(data, status) {
                NotificationSrv.error('AboutCtrl', data, status);
            });

            $scope.close = function() {
                $uibModalInstance.close();
            };
        }
    );
})();
