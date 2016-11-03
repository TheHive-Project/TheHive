(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('StatSrv', function($http, AlertSrv, UtilsSrv) {
            function getPromise(config) {
                var stats = [];

                if (config.field) {
                    var agg = {
                        _agg: 'field',
                        _field: config.field,
                        _select: [{
                            _agg: 'count'
                        }]
                    };

                    if (config.sort) {
                        agg._order = config.sort;
                    }

                    if (config.limit) {
                        agg._size = config.limit;
                    }

                    stats.push(agg);
                }

                stats.push({
                    _agg: 'count'
                });

                return $http.post('/api/' + config.objectType.replace(/_/g, '/') + '/_stats', {
                        query: config.query,
                        stats: stats
                    })
                    .then(function(ret) {
                        return ret;
                    });
            }


            function get(config) {
                var result;

                if (!angular.isObject(config.result)) {
                    result = {};
                } else {
                    result = config.result;
                }

                getPromise(config).then(function(r) {
                    UtilsSrv.shallowClearAndCopy(r.data, result);
                    if (angular.isFunction(config.success)) {
                        config.success(r.data, r.status, r.headers, r.config);
                    }
                }, function(r) {
                    if (angular.isFunction(config.error)) {
                        config.error(r.data, r.status, r.headers, r.config);
                    } else {
                        AlertSrv.error('StatSrv', r.data, r.status);
                    }
                });
                return result;
            }

            return {
                'get': get,
                'getPromise': getPromise
            };
        });
})();
