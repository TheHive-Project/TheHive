(function() {
    'use strict';
    angular.module('theHiveControllers').controller('SettingsCtrl',
        function($scope, $state, UserSrv, AuthenticationSrv, NotificationSrv, resizeService, readLocalPicService, currentUser, appConfig) {
            $scope.currentUser = currentUser;
            $scope.appConfig = appConfig;

            if(!currentUser || !currentUser.login) {
                $state.go('login');
                return;
            }

            $scope.basicData = {
                username: $scope.currentUser.login,
                name: $scope.currentUser.name,
                avatar: $scope.currentUser.avatar,
                avatarB64: null
            };

            $scope.passData = {
                changePassword: false,
                currentPassword: '',
                password: '',
                passwordConfirm: ''
            };
            $scope.canChangePass = appConfig.config.capabilities.indexOf('changePassword') !== -1;


            $scope.updateBasicInfo = function(form) {
                if (!form.$valid) {
                    return;
                }

                var postData = {
                    name: $scope.basicData.name
                };

                if($scope.basicData.avatarB64) {
                    postData.avatar = $scope.basicData.avatarB64;
                }

                if($scope.basicData.avatar === '') {
                    postData.avatar = '';
                }

                UserSrv.update($scope.currentUser.login, postData)
                    .then(function() {
                        return AuthenticationSrv.current();
                    })
                    .then(function(data) {
                        $scope.currentUser.name = data.name;

                        UserSrv.updateCache(data.login, data);

                        NotificationSrv.log('Your basic information have been successfully updated', 'success');

                        $state.reload();
                    })
                    .catch(function(response) {
                        NotificationSrv.error('SettingsCtrl', response.data, response.status);
                    });
            };

            $scope.updatePassword = function(form) {
                if (!form.$valid) {
                    return;
                }

                var updatedFields = {};
                if ($scope.passData.currentPassword && $scope.passData.password !== '' && $scope.passData.password === $scope.passData.passwordConfirm) {
                    updatedFields.currentPassword = $scope.passData.currentPassword;
                    updatedFields.password = $scope.passData.password;
                }

                if (updatedFields !== {}) {
                    UserSrv.changePass($scope.currentUser.login, updatedFields)
                        .then(function( /*data*/ ) {
                            NotificationSrv.log('Your password has been successfully updated', 'success');
                            $state.reload();
                        })
                        .catch(function(response) {
                            NotificationSrv.error('SettingsCtrl', response.data, response.status);
                        });
                } else {
                    $state.go('app.cases');
                }
            };

            $scope.clearPassword = function(form, changePassword) {

                if (!changePassword) {
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
                $state.go('app.index');
            };

            $scope.clearAvatar = function(form) {
                $scope.basicData.avatar = '';
                $scope.basicData.avatarB64 = null;
                form.avatar.$setValidity('maxsize', true);
                form.avatar.$setPristine(true);
            };

            $scope.$watch('avatarB64', function(value) {
               if(!value){
                   return;
               }

               resizeService.resizeImage('data:' + value.filetype + ';base64,' + value.base64, {
                   height: 100,
                   width: 100,
                   outputFormat: 'image/jpeg'
               })
               .then(function(image) {
                   $scope.basicData.avatarB64 = image.replace('data:image/jpeg;base64,', '');
                   $scope.basicData.avatar = null;
               });
           });
        }
    );
})();
