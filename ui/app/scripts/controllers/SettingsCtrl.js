(function() {
    'use strict';
    angular.module('theHiveControllers').controller('SettingsCtrl',
        function($scope, $state, UserSrv, AlertSrv, appConfig) {
            $scope.appConfig = appConfig;

            $scope.basicData = {
                username: $scope.currentUser.id,
                name: $scope.currentUser.name
            };

            $scope.passData = {
                changePassword: false,
                currentPassword: '',
                password: '',
                passwordConfirm: ''
            };
            $scope.canChangePass = appConfig.config.capabilities.indexOf('changePassword') !== -1;            

            $scope.updateBasicInfo = function(form) {
                if(!form.$valid) {
                    return;
                }

                UserSrv.update({
                    userId: $scope.currentUser.id
                }, {name: $scope.basicData.name}, function(data) {
                    $scope.currentUser.name = data.name;

                    AlertSrv.log('Your basic information have been successfully updated', 'success');

                    $state.reload();
                }, function(response) {
                    AlertSrv.error('SettingsCtrl', response.data, response.status);
                });
            };

            $scope.updatePassword = function(form) {
                if(!form.$valid) {
                    return;
                }

                var updatedFields = {};
                if ($scope.passData.currentPassword && $scope.passData.password !== '' && $scope.passData.password === $scope.passData.passwordConfirm) {
                    updatedFields.currentPassword = $scope.passData.currentPassword;
                    updatedFields.password = $scope.passData.password;
                }

                if (updatedFields !== {}) {
                    UserSrv.changePass({
                        userId: $scope.currentUser.id
                    }, updatedFields, function(/*data*/) {
                        AlertSrv.log('Your password has been successfully updated', 'success');
                        $state.reload();
                    }, function(response) {
                        AlertSrv.error('SettingsCtrl', response.data, response.status);
                    });
                } else {
                    $state.go('app.main');
                }
            };

            $scope.clearPassword = function(form, changePassword) {

                if(!changePassword) {
                    $scope.passData.currentPassword = '';
                    $scope.passData.password = '';
                    $scope.passData.passwordConfirm = '';
                }

                form.$setValidity('currentPassword', true);
                form.$setValidity('password', true);
                form.$setValidity('passwordConfirm', true);
                form.$setPristine(true);
            };

            $scope.cancel = function() {
                $state.go('app.main');
            };
        }
    );
})();
