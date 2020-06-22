(function() {
    'use strict';
    angular
        .module('theHiveServices')
        .factory('AuthenticationSrv', function($http, $q, UtilsSrv, SecuritySrv, UserSrv) {
            var self = {
                currentUser: null,
                homeState: null,
                login: function(username, password, code) {
                    var post = {
                        user: username,
                        password: password
                    };

                    if(code) {
                        post.code = code;
                    }

                    return $http.post('./api/login', post);
                },
                logout: function(success, failure) {
                    $http
                        .get('./api/logout')
                        .then(function(data, status, headers, config) {
                            self.currentUser = null;

                            if (angular.isFunction(success)) {
                                success(data, status, headers, config);
                            }
                        })
                        .catch(function(data, status, headers, config) {
                            if (angular.isFunction(failure)) {
                                failure(data, status, headers, config);
                            }
                        });
                },
                current: function(organisation) {
                    var result = {};

                    var options = {};
                    if(organisation) {
                        options.headers = {
                            'X-Organisation': organisation
                        };
                    }

                    return $http
                        .get('./api/v1/user/current', options)
                        .then(function(response) {
                            var userData = response.data;

                            self.currentUser = userData;

                            if(self.isSuperAdmin()) {
                                self.currentUser.homeState = 'app.administration.organisations';
                            } else {
                                self.currentUser.homeState = 'app.cases';
                            }

                            UserSrv.updateCache(self.currentUser.login, self.currentUser);
                            UtilsSrv.shallowClearAndCopy(self.currentUser, result);

                            return $q.resolve(result);
                        })
                        .catch(function(err) {
                            self.currentUser = null;
                            return $q.reject(err);
                        });
                },
                ssoLogin: function(code, state) {
                    var url = angular.isDefined(code) && angular.isDefined(state) ? './api/ssoLogin?code=' + code + '&state=' + state : './api/ssoLogin';
                    return $http.post(url, {});
                },
                isSuperAdmin: function() {
                    var user = self.currentUser;

                    return user && user.organisation === 'admin';
                },
                hasPermission: function(permissions) {
                    var user = self.currentUser;

                    if (!user) {
                        return false;
                    }

                    //return !_.isEmpty(_.intersection(user.permissions, permissions));

                    return SecuritySrv.checkPermissions(user.permissions, permissions);
                }
            };

            return self;
        });
})();
