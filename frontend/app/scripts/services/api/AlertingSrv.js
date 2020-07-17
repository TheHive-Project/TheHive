(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('AlertingSrv', function($q, $http, $rootScope, StatSrv, StreamSrv, PSearchSrv, PaginatedQuerySrv, QuerySrv) {

            var baseUrl = './api/alert';

            var factory = {

                list: function(config, callback) {
                    return new PaginatedQuerySrv({
                        name: 'alerts',
                        root: undefined,
                        objectType: 'alert',
                        version: 'v1',
                        scope: config.scope,
                        sort: config.sort || ['-date'],
                        loadAll: config.loadAll || false,
                        pageSize: config.pageSize || 10,
                        filter: config.filter || undefined,
                        onUpdate: callback || undefined,
                        operations: [
                            {'_name': 'listAlert'}
                        ]
                    });
                },

                get: function(alertId) {
                    return QuerySrv.call('v1', [{
                            '_name': 'getAlert',
                            'idOrName': alertId
                        }
                    ], {
                        name: 'get-alert-' + alertId
                    }).then(function(response) {
                        return response[0];
                    });
                },

                create: function(alertId, data) {
                    return $http.post(baseUrl + '/' + alertId + '/createCase', data || {});
                },

                update: function(alertId, updates) {
                    return $http.patch(baseUrl + '/' + alertId, updates);
                },

                mergeInto: function(alertId, caseId) {
                    return $http.post(baseUrl + '/' + alertId + '/merge/' + caseId);
                },

                bulkMergeInto: function(alertIds, caseId) {
                    return $http.post(baseUrl + '/merge/_bulk', {
                        caseId: caseId,
                        alertIds: alertIds
                    });
                },

                canMarkAsRead: function(event) {
                    return !!!event.read;
                },

                canMarkAsUnread: function(event) {
                    return !!event.read;
                },

                markAsRead: function(alertId) {
                    return $http.post(baseUrl + '/' + alertId + '/markAsRead');
                },

                markAsUnread: function(alertId) {
                    return $http.post(baseUrl + '/' + alertId + '/markAsUnread');
                },

                follow: function(alertId) {
                    return $http.post(baseUrl + '/' + alertId + '/follow');
                },

                unfollow: function(alertId) {
                    return $http.post(baseUrl + '/' + alertId + '/unfollow');
                },

                forceRemove: function(alertId) {
                    return $http.delete(baseUrl + '/' + alertId, {
                        params: {
                            force: 1
                        }
                    });
                },

                bulkRemove: function(alertIds) {
                    return $http.post(baseUrl + '/delete/_bulk', {
                        ids: alertIds
                    }, {
                        params: {
                            force: 1
                        }
                    });
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
                }

            };

            return factory;
        });

})();
