(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('AlertingSrv', function($q, $http, $rootScope, StatSrv, StreamSrv, PSearchSrv) {

            var baseUrl = './api/alert';

            var factory = {

                list: function(config, callback) {
                    return PSearchSrv(undefined, 'alert', {
                        scope: config.scope,
                        sort: config.sort || '-date',
                        loadAll: config.loadAll || false,
                        pageSize: config.pageSize || 10,
                        filter: config.filter || '',
                        onUpdate: callback || angular.noop,
                        streamObjectType: 'alert'
                    });
                },

                get: function(alertId) {
                    return $http.get(baseUrl + '/get/' + alertId);
                },

                create: function(alertId) {
                    return $http.post(baseUrl + '/case/' + alertId, {});
                },

                ignore: function(alertId) {
                    return $http.get(baseUrl + '/ignore/' + alertId);
                },

                follow: function(alertId) {
                    return $http.get(baseUrl + '/follow/' + alertId);
                },

                unfollow: function(alertId) {
                    return $http.get(baseUrl + '/unfollow/' + alertId);
                },

                stats: function(scope) {
                    var field = 'status',
                        result = {},
                        statConfig = {
                            query: {},
                            objectType: 'alert',
                            field: field,
                            result: result
                        };

                    StreamSrv.addListener({
                        rootId: 'any',
                        objectType: 'alert',
                        scope: scope,
                        callback: function() {
                            StatSrv.get(statConfig);
                        }
                    });

                    return StatSrv.get(statConfig);
                },

                sources: function(query) {
                    var defer = $q.defer();

                    StatSrv.getPromise({
                        objectType: 'alert',
                        field: 'source',
                        limit: 1000
                    }).then(function(response) {
                        var sources = [];

                        sources = _.map(_.filter(_.keys(response.data), function(source) {
                            var regex = new RegExp(query, 'gi');
                            return regex.test(source);
                        }), function(source) {
                            return {text: source};
                        });

                        defer.resolve(sources);
                    });

                    return defer.promise;
                },

                statuses: function(query) {
                    var defer = $q.defer();

                    $q.resolve([
                        {text: 'New'},
                        {text: 'Update'},
                        {text: 'Imported'},
                        {text: 'Ignore'}
                    ]).then(function(response) {
                        var statuses = [];

                        statuses = _.filter(response, function(status) {
                            var regex = new RegExp(query, 'gi');
                            return regex.test(status.text);
                        });

                        defer.resolve(statuses);
                    });

                    return defer.promise;
                }
            };

            return factory;
        });

})();
