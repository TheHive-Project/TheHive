/**
 * Controller for login modal page2
 */
(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('AuthenticationCtrl', function($scope, $state, $uibModalStack, AuthenticationSrv, NotificationSrv) {
            $scope.params = {};

            $uibModalStack.dismissAll();

            $scope.login = function() {
                $scope.params.username = angular.lowercase($scope.params.username);
                AuthenticationSrv.login($scope.params.username, $scope.params.password, function() {
                    $state.go('app.cases');
                }, function(data, status) {
                    if (status === 520) {
                        NotificationSrv.error('AuthenticationCtrl', data, status);
                    } else {
                        NotificationSrv.log(data.message, 'error');
                    }
                });
            };
        });
})();
