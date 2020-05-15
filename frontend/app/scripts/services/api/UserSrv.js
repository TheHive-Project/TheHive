(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('UserSrv', function($http, $q, $uibModal, QuerySrv) {

            var self = this;

            this.userCache = {};

            this.query = function(config) {
                return $http.post('./api/v1/user/_search', config)
                    .then(function(response) {
                        return $q.resolve(response.data);
                    })
                    .catch(function(err) {
                        return $q.reject(err);
                    });
            };

            this.get = function(user) {
                if (!user) {
                    return;
                }
                return $http
                    .get('./api/v1/user/' + user)
                    .then(function(response) {
                        return $q.resolve(response.data);
                    })
                    .catch(function(err) {
                        return $q.reject(err);
                    });
            };

            this.save = function(user) {
                return $http
                    .post('./api/v1/user', user)
                    .then(function(response) {
                        return $q.resolve(response.data);
                    })
                    .catch(function(err) {
                        return $q.reject(err);
                    });
            };

            this.update = function(id, user) {
                var defer = $q.defer();

                $http
                    .patch('./api/v1/user/' + id, user)
                    .then(function(response) {
                        defer.resolve(response.data);
                    })
                    .catch(function(err) {
                        return $q.reject(err);
                    });

                return defer.promise;
            };

            this.remove = function(id, organisation) {
                return $http
                    .delete('./api/v1/user/' + id + '/force', {
                        params: {organisation: organisation}
                    })
                    .then(function(response) {
                        return $q.resolve(response.data);
                    })
                    .catch(function(err) {
                        return $q.reject(err);
                    });
            };

            this.changePass = function(id, currentPassword, password) {
                return $http
                    .post('./api/v1/user/' + id + '/password/change', {
                        currentPassword: currentPassword,
                        password: password
                    })
                    .then(function(response) {
                        return $q.resolve(response.data);
                    })
                    .catch(function(err) {
                        return $q.reject(err);
                    });
            };

            this.setPass = function(id, password) {
                return $http
                    .post('./api/v1/user/' + id + '/password/set', {
                        password: password
                    })
                    .then(function(response) {
                        return $q.resolve(response.data);
                    })
                    .catch(function(err) {
                        return $q.reject(err);
                    });
            };

            this.setKey = function(id) {
                return $http
                    .post('./api/v1/user/' + id + '/key/renew')
                    .then(function(response) {
                        return $q.resolve(response.data);
                    })
                    .catch(function(err) {
                        return $q.reject(err);
                    });
            };

            this.revokeKey = function(id) {
                return $http
                    .delete('./api/v1/user/' + id + '/key')
                    .then(function(response) {
                        return $q.resolve(response.data);
                    })
                    .catch(function(err) {
                        return $q.reject(err);
                    });
            };

            this.getUserInfo = function(login) {
                var defer = $q.defer();

                if (login === 'init') {
                    defer.resolve({
                        name: 'System'
                    });
                } else {
                    if (!login) {
                        defer.reject(undefined);
                        return;
                    }

                    self.get(login)
                        .then(function(user) {
                            defer.resolve(user);
                        })
                        .catch(function(err) {
                            err.data.name = '***unknown***';
                            defer.reject(err);
                        });
                }

                return defer.promise;
            };

            this.getKey = function(userId) {
                return $http
                    .get('./api/v1/user/' + userId + '/key')
                    .then(function(response) {
                        return $q.resolve(response.data);
                    })
                    .catch(function(err) {
                        return $q.reject(err);
                    });
            };

            this.fetchMfaSecret = function() {
                return $http
                    .post('./api/v1/auth/totp/set', {});
            };

            this.setMfa = function(code) {
                return $http
                    .post('./api/v1/auth/totp/set', {
                        code: code
                    });
            };

            this.resetMfa = function(user) {
                var url = './api/v1/auth/totp/unset';

                return $http.post(user ? (url+'/'+ user) : url );
            };

            this.list = function(organisation, query) {
                // var post = {
                //     range: 'all',
                //     query: query
                // };
                // return $http
                //     .post('./api/v1/user/_search', post)
                //     .then(function(response) {
                //         return $q.resolve(response.data);
                //     })
                //     .catch(function(err) {
                //         return $q.reject(err);
                //     });

                var operations = [{
                        '_name': 'getOrganisation',
                        'idOrName': organisation
                    },
                    {
                        '_name': 'users'
                    }
                ];

                if (query) {
                    query._name = 'filter';

                    operations.push(query);
                }

                return QuerySrv.query('v1', operations)
                    .then(function(response) {
                        return $q.resolve(response.data);
                    });
            };

            this.autoComplete = function(organisation, query) {
                return this.list(organisation, {
                        _is: {
                            locked: false
                        }
                    })
                    .then(function(data) {
                        return _.map(data, function(user) {
                            return {
                                label: user.name,
                                text: user.login
                            };
                        });
                    })
                    .then(function(users) {
                        return _.filter(users, function(user) {
                            var regex = new RegExp(query, 'gi');

                            return regex.test(user.label);
                        });
                    });
            };

            this.getCache = function(userId) {
                if (angular.isDefined(self.userCache[userId])) {
                    return $q.resolve(self.userCache[userId]);
                } else {
                    var defer = $q.defer();

                    self.getUserInfo(userId)
                        .then(function(userInfo) {
                            self.userCache[userId] = userInfo;
                            defer.resolve(userInfo);
                        })
                        .catch(function( /*err*/ ) {
                            defer.resolve(undefined);
                        });

                    return defer.promise;
                }
            };

            this.clearCache = function() {
                self.userCache = {};
            };

            this.removeCache = function(userId) {
                delete self.userCache[userId];
            };

            this.updateCache = function(userId, userData) {
                self.userCache[userId] = userData;
            };

            this.openModal = function(user, organisation) {
                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/admin/organisation/user.modal.html',
                    controller: 'OrgUserModalCtrl',
                    controllerAs: '$modal',
                    size: 'lg',
                    resolve: {
                        organisation: $q.resolve(organisation),
                        user: angular.copy(user) || {},
                        profiles: function(ProfileSrv) {
                            return ProfileSrv.map();
                        },
                        isEdit: !!user
                    }
                });

                return modalInstance.result;
            };

        });
})();
