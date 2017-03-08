(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('MispSrv', function($q, $http, $rootScope, StatSrv, StreamSrv, PSearchSrv) {

            var baseUrl = './api/connector/misp';

            var factory = {
                list: function(scope, callback) {
                    return PSearchSrv(undefined, 'connector/misp', {
                        scope: scope,
                        sort: '-publishDate',
                        loadAll: false,
                        pageSize: 10,
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
                        mispStatQuery = {
                            _not: {
                                _in: {
                                    _field: 'eventStatus',
                                    _values: ['Ignore', 'Imported']
                                }
                            }
                        },
                        result = {},
                        statConfig = {
                            query: mispStatQuery,
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
                }
            };

            return factory;
        });

})();
