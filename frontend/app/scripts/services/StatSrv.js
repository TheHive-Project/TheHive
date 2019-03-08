(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('StatSrv', function($http, NotificationSrv, UtilsSrv) {
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

                if(!config.skipTotal) {
                  stats.push({
                      _agg: 'count'
                  });
                }

                var entity = config.objectType.replace(/_/g, '/');
                if(entity[0] === '/') {
                    entity = entity.substr(1);
                }

                return $http.post('./api/' + entity + '/_stats', {
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
                        NotificationSrv.error('StatSrv', r.data, r.status);
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
