(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminUsersCtrl',
        function($scope, $uibModal, PSearchSrv, UserSrv, NotificationSrv, clipboard, appConfig) {
            $scope.appConfig = appConfig;
            $scope.canSetPass = appConfig.config.capabilities.indexOf('setPassword') !== -1;
            $scope.newUser = {
                roles: ['read','write']
            };

            /**
             * users management page
             */
            $scope.userlist = PSearchSrv(undefined, 'user', {
                scope: $scope,
                sort: '+name',
            });
            $scope.initNewUser = function() {
                $scope.apiKey = false;
                $scope.newUser = {
                    roles: ['read','write']
                };
            };
            $scope.initNewUser();

            $scope.addUser = function(user) {
                user.login = user.login.toLowerCase();
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
                UserSrv.getKey(user.id)
                    .then(function(key) {
                        $scope.usrKey[user.id] = key;
                    });

            };
            $scope.createKey = function(user) {
                UserSrv.setKey({
                    userId: user.id
                },{}, function() {
                    delete $scope.usrKey[user.id];
                }, function(response) {
                    NotificationSrv.error('AdminUsersCtrl', response.data, response.status);
                });
            };

            $scope.revokeKey = function(user) {
                UserSrv.revokeKey({
                    userId: user.id
                },{}, function() {
                    delete $scope.usrKey[user.id];
                }, function(response) {
                    NotificationSrv.error('AdminUsersCtrl', response.data, response.status);
                });
            };

            $scope.copyKey = function(user) {
                clipboard.copyText($scope.usrKey[user.id]);
                delete $scope.usrKey[user.id];
            };

            $scope.updateField = function(user, fieldName, newValue) {
                var field = {};
                field[fieldName] = newValue;
                var userModified = user;
                return UserSrv.update({
                    userId: userModified.id
                }, field, function() {}, function(response) {
                    NotificationSrv.error('UserMgmtCtrl', response.data, response.status);
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
                    NotificationSrv.log('The password of user [' + user.id + '] has been successfully updated', 'success');
                }, function(response) {
                    NotificationSrv.error('UserMgmtCtrl', response.data, response.status);
                });
            };

            $scope.showUserDialog = function(user) {
                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/admin/user-dialog.html',
                    controller: 'AdminUserDialogCtrl',
                    controllerAs: '$vm',
                    size: 'lg',
                    resolve: {
                        user: angular.copy(user) || {},
                        isEdit: !!user
                    }
                });

                modalInstance.result.then(function(/*data*/) {
                    //self.initCustomfields();
                    //CustomFieldsCacheSrv.clearCache();
                    //$scope.$emit('custom-fields:refresh');
                });
            };

        });

})();
