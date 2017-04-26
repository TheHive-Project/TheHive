(function() {
    'use strict';
    angular.module('theHiveControllers').controller('SettingsCtrl',
        function($scope, $state, UserSrv, NotificationSrv, resizeService, readLocalPicService, UserInfoSrv, currentUser, appConfig) {
            $scope.currentUser = currentUser;
            $scope.appConfig = appConfig;

            if(!currentUser || !currentUser.id) {
                $state.go('login');
                return;
            }

            $scope.basicData = {
                username: $scope.currentUser.id,
                name: $scope.currentUser.name,
                avatar: $scope.currentUser.avatar
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

                UserSrv.update({
                    userId: $scope.currentUser.id
                }, {
                    name: $scope.basicData.name,
                    avatar: $scope.basicData.avatar
                }, function(data) {
                    $scope.currentUser.name = data.name;

                    UserInfoSrv.update(data._id, data);

                    NotificationSrv.log('Your basic information have been successfully updated', 'success');

                    $state.reload();
                }, function(response) {
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
                    UserSrv.changePass({
                        userId: $scope.currentUser.id
                    }, updatedFields, function( /*data*/ ) {
                        NotificationSrv.log('Your password has been successfully updated', 'success');
                        $state.reload();
                    }, function(response) {
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
                $state.go('app.cases');
            };

            $scope.clearAvatar = function(form) {
                $scope.basicData.avatar = null;
                form.avatar.$setValidity('maxsize', true);
                form.avatar.$setPristine(true);
            };

            $scope.$watch('avatar', function(value) {
               if(!value){
                   return;
               }

               resizeService.resizeImage('data:' + value.filetype + ';base64,' + value.base64, {
                   height: 100,
                   width: 100,
                   outputFormat: 'image/jpeg'
               })
               .then(function(image) {
                   $scope.basicData.avatar = image.replace('data:image/jpeg;base64,', '');
               });
           });
        }
    );
})();
