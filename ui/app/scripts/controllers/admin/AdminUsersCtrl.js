(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminUsersCtrl',
        function($scope, PSearchSrv, UserSrv, AlertSrv, appConfig) {
            $scope.appConfig = appConfig;
            $scope.canSetPass = appConfig.config.capabilities.indexOf('setPassword') !== -1;
            $scope.newUser = {
                roles: ['read','write']
            };

            /**
             * users management page
             */
            $scope.userlist = PSearchSrv(undefined, 'user', {
                scope: $scope
            });
            $scope.initNewUser = function() {
                $scope.apiKey = false;
                $scope.newUser = {
                    roles: ['read','write']
                };
            };
            $scope.initNewUser();

            $scope.addUser = function(user) {
                user.login = angular.lowercase(user.login);
                if ($scope.apiKey) {
                    user['with-key'] = true;
                }
                UserSrv.save(user);
                $scope.initNewUser();
            };

            $scope.deleteUser = function(user) {
                UserSrv['delete']({
                    userId: user.id
                });
            };

            $scope.lockUser = function(user) {
                if (user.status === 'Locked') {
                    $scope.updateField(user, 'status', 'Ok');
                } else {
                    $scope.updateField(user, 'status', 'Locked');
                }
            };

            $scope.usrKey = {};
            $scope.getKey = function(user) {
                UserSrv.get({
                    userId: user.id
                }, function(usr) {
                    $scope.usrKey[user.id] = usr.key;
                });

            };
            $scope.createKey = function(user) {
                $scope.updateField(user, 'with-key', true);
            };

            $scope.updateField = function(user, fieldName, newValue) {
                var field = {};
                field[fieldName] = newValue;
                var userModified = user;
                return UserSrv.update({
                    userId: userModified.id
                }, field, function() {}, function(response) {
                    AlertSrv.error('UserMgmtCtrl', response.data, response.status);
                });
            };

            $scope.setPassword = function(user, password) {
                if (!$scope.canSetPass) {
                    return;
                }

                UserSrv.setPass({
                    userId: user.id
                }, {
                    password: password
                }, function() {
                    AlertSrv.log('The password of user [' + user.id + '] has been successfully updated', 'success');
                }, function(response) {
                    AlertSrv.error('UserMgmtCtrl', response.data, response.status);
                });
            };

        });

})();
