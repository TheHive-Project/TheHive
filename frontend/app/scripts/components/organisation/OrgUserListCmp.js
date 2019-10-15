(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('orgUserList', {
            controller: function($scope, UserSrv, NotificationSrv, ModalSrv, AuthenticationSrv, clipboard) {
                var self = this;

                self.userKeyCache = {};
                self.showPwdForm = {};
                self.currentUser = AuthenticationSrv.currentUser;

                self.$onInit = function() {
                    // TODO FIX ME
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
                            self.onReload();
                            NotificationSrv.success(
                                'API key of user ' + user.login + ' has been successfully created.'
                            );
                        })
                        .catch(function(err) {
                            if (!_.isString(err)) {
                                NotificationSrv.error('OrgUserCtrl', err.data, err.status);
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
                            self.onReload();
                            NotificationSrv.success(
                                'API key of user ' + user.login + ' has been successfully revoked.'
                            );
                        })
                        .catch(function(err) {
                            if (err && !_.isString(err)) {
                                NotificationSrv.error('OrgUserCtrl', err.data, err.status);
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
                            NotificationSrv.success('Password of user ' + user.login + ' has been successfully updated.');
                            self.onReload();
                        })
                        .catch(function(response) {
                            NotificationSrv.error(
                                'OrgUserCtrl',
                                response.data,
                                response.status
                            );
                        });
                };

                self.editUser = function(user) {
                    self.onEdit({
                        user: user
                    });
                };

                self.lockUser = function(user, locked) {
                    var action = (locked ? 'lock' : 'unlock');

                    var modalInstance = ModalSrv.confirm(
                        (locked ? 'Lock' : 'Unlock') + ' User',
                        'Are you sure you want to ' + action +' this user?', {
                            flavor: 'danger',
                            okText: 'Yes, proceed'
                        }
                    );

                    modalInstance.result
                        .then(function(/*response*/) {
                            return UserSrv.update(user._id, {locked: locked});
                        })
                        .then(function() {
                            NotificationSrv.success('User ' + user.login + ' has been successfully updated.');
                            self.onReload();
                        })
                        .catch(function(response) {
                            NotificationSrv.error(
                                'OrgUserCtrl',
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
                onReload: '&',
                onEdit: '&'
            }
        });
})();
