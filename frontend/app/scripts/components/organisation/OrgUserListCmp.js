(function() {
    'use strict';

    angular.module('theHiveControllers')
        .component('orgUserList', {
            controller: function($scope, UserSrv, NotificationSrv, ModalSrv, clipboard) {
                var self = this;

                self.userKeyCache = {};
                self.showPwdForm = {};                

                self.$onInit = function() {
                    self.canSetPass = true;
                };

                self.reload = function() {
                    self.onReload();
                };

                self.showPassword = function(user, visible) {
                    self.showPwdForm[user._id] = visible;
                    if (visible) {
                        $scope.$broadcast('user-showPassword-' + user._id);
                    }
                };

                self.getKey = function(user) {
                    UserSrv.getKey(user._id).then(function(key) {
                        self.userKeyCache[user._id] = key;
                    });
                };

                self.createKey = function(user) {
                    var modalInstance = ModalSrv.confirm(
                        'Create API key',
                        'Are you sure you want to create a new API key for this user?', {
                            okText: 'Yes, create it'
                        }
                    );

                    modalInstance.result
                        .then(function() {
                            return UserSrv.setKey(user._id);
                        })
                        .then(function( /*response*/ ) {
                            delete self.userKeyCache[user._id];
                            self.reload();
                            NotificationSrv.success(
                                'API key of user ' + user.login + ' has been successfully created.'
                            );
                        })
                        .catch(function(err) {
                            if (!_.isString(err)) {
                                NotificationSrv.error('AdminUsersCtrl', err.data, err.status);
                            }
                        });
                };

                self.revokeKey = function(user) {
                    var modalInstance = ModalSrv.confirm(
                        'Revoke API key',
                        'Are you sure you want to revoke the API key of this user?', {
                            flavor: 'danger',
                            okText: 'Yes, revoke it'
                        }
                    );

                    modalInstance.result
                        .then(function() {
                            return UserSrv.revokeKey(user._id);
                        })
                        .then(function( /*response*/ ) {
                            delete self.userKeyCache[user._id];
                            self.reload();
                            NotificationSrv.success(
                                'API key of user ' + user.login + ' has been successfully revoked.'
                            );
                        })
                        .catch(function(err) {
                            if (!_.isString(err)) {
                                NotificationSrv.error('AdminUsersCtrl', err.data, err.status);
                            }
                        });
                };

                self.copyKey = function(user) {
                    clipboard.copyText(self.userKeyCache[user._id]);
                    delete self.userKeyCache[user._id];
                    NotificationSrv.success(
                        'API key of user ' + user.login + ' has been successfully copied to clipboard.'
                    );
                };

                self.setPassword = function(user, password) {
                    if (!self.canSetPass) {
                        return;
                    }

                    UserSrv.setPass(user._id, password)
                        .then(function() {
                            NotificationSrv.log('Password of user ' + user.login + ' has been successfully updated.');
                        })
                        .catch(function(response) {
                            NotificationSrv.error(
                                'OrganizationUsersListController',
                                response.data,
                                response.status
                            );
                        });
                };
            },
            controllerAs: '$ctrl',
            templateUrl: 'views/components/org/user.list.html',
            bindings: {
                users: '<',
                onReload: '&'
            }
        });
})();
