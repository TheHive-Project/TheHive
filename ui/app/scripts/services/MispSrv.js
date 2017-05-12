(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('MispSrv', function($q, $http, $rootScope, StatSrv, StreamSrv, PSearchSrv) {

            var baseUrl = './api/connector/misp';

            var factory = {

                list: function(config, callback) {
                    return PSearchSrv(undefined, 'connector/misp', {
                        scope: config.scope,
                        sort: config.sort || '-publishDate',
                        loadAll: config.loadAll || false,
                        pageSize: config.pageSize || 10,
                        filter: config.filter || '',
                        onUpdate: callback || angular.noop,
                        streamObjectType: 'misp'
                    });
                },

                get: function(mispId) {
                    return $http.get(baseUrl + '/get/' + mispId);
                },

                create: function(mispId) {
                    return $http.post(baseUrl + '/case/' + mispId, {});
                },

                ignore: function(mispId) {
                    return $http.get(baseUrl + '/ignore/' + mispId);
                },

                follow: function(mispId) {
                    return $http.get(baseUrl + '/follow/' + mispId);
                },

                unfollow: function(mispId) {
                    return $http.get(baseUrl + '/unfollow/' + mispId);
                },

                onSuccess: function() {
                    $rootScope.$broadcast('misp:status-updated', true);
                },

                onFailure: function() {
                    $rootScope.$broadcast('misp:status-updated', false);
                },

                stats: function(scope) {
                    var field = 'eventStatus',
                        result = {},
                        statConfig = {
                            query: {},
                            objectType: 'connector/misp',
                            field: field,
                            result: result,
                            success: factory.onSuccess,
                            error: factory.onFailure
                        };



                    StreamSrv.addListener({
                        rootId: 'any',
                        objectType: 'misp',
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
                        objectType: 'connector/misp',
                        field: 'org',
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
