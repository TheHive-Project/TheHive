(function() {
    'use strict';
    angular.module('theHiveControllers').controller('SettingsCtrl',
        function($scope, $state, UserSrv, ModalSrv, AuthenticationSrv, NotificationSrv, clipboard, resizeService, readLocalPicService, currentUser, appConfig) {
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

            $scope.keyData = {};

            $scope.mfaData = {
                enabled: $scope.currentUser.hasMFA,
                secret: null,
                uri: null,
                code: null
            };

            $scope.canChangePass = appConfig.config.capabilities.indexOf('changePassword') !== -1;
            $scope.canChangeKey = appConfig.config.capabilities.indexOf('authByKey') !== -1;
            $scope.canChangeMfa = appConfig.config.capabilities.indexOf('mfa') !== -1;


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
                    UserSrv.changePass($scope.currentUser.login, updatedFields.currentPassword, updatedFields.password)
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

            $scope.createKey = function(){
                    var modalInstance = ModalSrv.confirm(
                        'Renew API Key',
                        'Are you sure you want to renew your API Key?', {
                            flavor: 'danger',
                            okText: 'Yes, renew it'
                        }
                    );

                    modalInstance.result
                        .then(function() {
                            UserSrv.setKey($scope.currentUser.login);
                        })
                        .then(function() {
                            delete $scope.keyData.key;
                            NotificationSrv.success('API key has been successfully renewed.');
                        })
                        .catch(function(err) {
                            if (!_.isString(err)) {
                                NotificationSrv.error('SettingsCtrl', err.data, err.status);
                            }
                        });
            };

            $scope.getKey = function() {
                UserSrv.getKey($scope.currentUser.login)
                    .then(function(key) {
                        $scope.keyData.key = key;
                    });
            };

            $scope.copyKey = function() {
                clipboard.copyText($scope.keyData.key);
                delete $scope.keyData.key;
                NotificationSrv.success('API key has been successfully copied to clipboard.');
            };

            $scope.copySecret = function(secret) {
                clipboard.copyText(secret);
                NotificationSrv.success('MFA Secret has been successfully copied to clipboard.');
            };

            $scope.enableMfa = function() {
                if($scope.mfaData.enabled) {
                    // Fetch the secret
                    UserSrv.fetchMfaSecret()
                        .then(function(response) {
                            $scope.mfaData.secret = response.data.secret;
                            $scope.mfaData.uri = response.data.uri;
                        })
                        .catch(function(err) {
                            NotificationSrv.error('SettingsCtrl', err.data, err.status);
                        });
                }
            };

            $scope.setMfaSettings = function(form) {
                UserSrv.setMfa($scope.mfaData.code)
                    .then(function(/*response*/) {
                        NotificationSrv.log('Your multi-factor authentication has been successfully configured', 'success');
                        $state.reload();
                    })
                    .catch(function(/*err*/) {
                        $scope.mfaData.enabled = true;
                        form.mfaCode.$setValidity('mfaInvalid', false);
                    });
            };

            $scope.resetMfa = function() {
                var modalInstance = ModalSrv.confirm(
                    'Disable MFA',
                    'Are you sure you want to disabble MFA settings?', {
                        okText: 'Yes, disable it',
                        flavor: 'danger'
                    }
                );

                modalInstance.result
                    .then(function() {
                        UserSrv.resetMfa();
                    })
                    .then(function() {
                        NotificationSrv.log('Your multi-factor authentication has been successfully disabled', 'success');
                        $state.reload();
                    })
                    .catch(function(err) {
                        if (!_.isString(err)) {
                            NotificationSrv.error('SettingsCtrl', err.data, err.status);
                        }
                    });
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
