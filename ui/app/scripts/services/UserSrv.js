angular.module('theHiveServices')
    .factory('UserSrv', function($resource, $http, $q, NotificationSrv, UtilsSrv) {
        'use strict';
        var res = $resource('./api/user/:userId', {}, {
            query: {
                method: 'POST',
                url: './api/user/_search',
                isArray: true
            },
            update: {
                method: 'PATCH'
            },
            changePass: {
                method: 'POST',
                url: './api/user/:userId/password/change'
            },
            setPass: {
                method: 'POST',
                url: './api/user/:userId/password/set'
            },
            // getKey: {
            //     method: 'GET',
            //     url: './api/user/:userId/key'
            // },
            setKey: {
                method: 'POST',
                url: './api/user/:userId/key/renew'
            },
            revokeKey: {
                method: 'DELETE',
                url: './api/user/:userId/key'
            }
        });
        /**
         * @Deprecated
         */
        res.getInfo = function(login) {
            if (!angular.isString(login)) {
                return login;
            }

            if (login === 'init') {
                return {
                    id: login,
                    name: 'System'
                };
            } else {
                var ret = {
                    'name': login,
                    'id': login
                };
                res.get({
                    'userId': login
                }, function(data) {
                    UtilsSrv.shallowClearAndCopy(data, ret);
                }, function(data, status) {
                    ret.name = '***unknown***';
                    NotificationSrv.error('UserSrv', data, status);
                });
                return ret;
            }

        };
        res.getUserInfo = function(login) {
            var defer = $q.defer();

            if (login === 'init') {
                defer.resolve({
                    name: 'System'
                });
            } else {
                res.get({
                    'userId': login
                }, function(user) {
                    defer.resolve(user);
                }, function(data, status) {
                    data.name = '***unknown***';
                    defer.reject(data, status);
                });
            }

            return defer.promise;
        };

        res.getKey = function(userId) {
            var defer = $q.defer();

            $http.get('./api/user/'+userId+'/key')
                .then(function(response) {
                    defer.resolve(response.data);
                });

            return defer.promise;
        };

        res.list = function(query) {
            var defer = $q.defer();

            var post = {
                range: 'all',
                query: query
            };

            $http.post('./api/user/_search', post)
                .then(function(response) {
                    defer.resolve(response.data);
                });

            return defer.promise;
        };

        return res;
    });
