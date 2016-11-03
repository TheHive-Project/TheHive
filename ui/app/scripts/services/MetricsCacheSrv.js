(function() {
    'use strict';
    angular.module('theHiveServices').factory('MetricsCacheSrv', function($resource, $q) {

        var metrics = null,
            resource = $resource('/api/list/:listId', {}, {
                query: {
                    method: 'GET',
                    isArray: false
                },
                add: {
                    method: 'PUT'
                }
            });

        return {
            clearCache: function() {
                metrics = null;
            },

            get: function(name) {
                return metrics[name];
            },

            all: function() {
                var deferred = $q.defer();

                if(metrics === null) {
                    resource.query({listId: 'case_metrics'}, {}, function(response) {
                        metrics = {};
                        var data = _.values(response).filter(_.isString).map(function(item) {
                            return JSON.parse(item);
                        });

                        _.each(data, function(m){
                            metrics[m.name] = m;
                        });

                        deferred.resolve(metrics);
                    }, function(response) {
                        deferred.reject(response);
                    });
                } else {
                    deferred.resolve(metrics);
                }

                return deferred.promise;
            }
        };
    });
})();
