(function() {
    'use strict';
    angular.module('theHiveServices').factory('AuthenticationSrv', function($http, UtilsSrv) {
        var self = {
            currentUser: null,
            login: function(username, password, success, failure) {
                $http.post('./api/login', {
                    'user': username,
                    'password': password
                }).success(function(data, status, headers, config) {
                    if (angular.isFunction(success)) {
                        success(data, status, headers, config);
                    }
                }).error(function(data, status, headers, config) {
                    if (angular.isFunction(failure)) {
                        failure(data, status, headers, config);
                    }
                });
            },
            logout: function(success, failure) {
                $http.get('./api/logout').success(function(data, status, headers, config) {
                    self.currentUser = null;

                    if (angular.isFunction(success)) {
                        success(data, status, headers, config);
                    }
                }).error(function(data, status, headers, config) {
                    if (angular.isFunction(failure)) {
                        failure(data, status, headers, config);
                    }
                });
            },
            current: function(success, failure) {
                var result = {};
                $http.get('./api/user/current').success(function(data, status, headers, config) {
                    self.currentUser = data;

                    UtilsSrv.shallowClearAndCopy(data, result);
                    if (angular.isFunction(success)) {
                        success(data, status, headers, config);
                    }
                }).error(function(data, status, headers, config) {
                    self.currentUser = null;
                    if (angular.isFunction(failure)) {
                        failure(data, status, headers, config);
                    }
                });
                return result;
            }
        };

        return self;
    });
})();
