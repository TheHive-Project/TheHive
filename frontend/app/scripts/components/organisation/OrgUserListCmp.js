(function() {
    'use strict';

    angular.module('theHiveControllers')
        .component('orgUserList', {
            controller: function($scope, UserSrv, NotificationSrv, clipboard) {
                var self = this;

                self.userKeyCache = {};
                self.showPwdForm = {};

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
                    UserSrv.setKey({
                        userId: user._id
                    },{}, function() {
                        //delete $scope.usrKey[user.id];
                        delete self.userKeyCache[user._id];
                        //self.reload();
                        NotificationSrv.success(
                            'API key of user ${user._id} has been successfully created.'
                        );
                    }, function(response) {
                        NotificationSrv.error('AdminUsersCtrl', response.data, response.status);
                    });
                    // UserSrv.setKey(user._id)
                    //     .then(function() {
                    //         delete self.userKeyCache[user._id];
                    //         self.reload();
                    //         NotificationSrv.success(
                    //             'API key of user ${user._id} has been successfully created.'
                    //         );
                    //     })
                    //     .catch(function(response) {
                    //         NotificationSrv.error(
                    //             'AdminUsersCtrl',
                    //             response.data,
                    //             response.status
                    //         );
                    //     });
                };

                self.revokeKey = function(user) {
                    UserSrv.revokeKey(user._id)
                        .then(function() {
                            delete self.userKeyCache[user._id];
                            self.reload();
                            NotificationSrv.success(
                                'API key of user ' + user._id + ' has been successfully revoked.'
                            );
                        })
                        .catch(function(response) {
                            NotificationSrv.error(
                                'OrganizationUsersListController',
                                response.data,
                                response.status
                            );
                        });
                };

                self.copyKey = function(user) {
                    clipboard.copyText(self.userKeyCache[user._id]);
                    delete self.userKeyCache[user._id];
                    NotificationSrv.success(
                        'API key of user '+user._id+' has been successfully copied to clipboard.'
                    );
                };

                self.setPassword = function(user, password) {
                    if (!self.canSetPass) {
                        return;
                    }

                    UserSrv.setPass(user._id, password)
                        .then(function(){
                            NotificationSrv.log('Password of user ' + user._id + ' has been successfully updated.');
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
                users: '<'
            }
        });
})();
