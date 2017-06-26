(function() {
    'use strict';
    angular.module('theHiveServices').factory('CustomFieldsCacheSrv', function($resource, $q) {

        var cache = null,
            resource = $resource('./api/list/:listId', {}, {
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
                cache = null;
            },

            get: function(name) {
                return cache[name];
            },

            all: function() {
                var deferred = $q.defer();

                if(cache === null) {
                    resource.query({listId: 'custom_fields'}, {}, function(response) {
                        cache = {};
                        var data = _.values(response).filter(_.isString).map(function(item) {
                            return JSON.parse(item);
                        });

                        _.each(data, function(field){
                            cache[field.reference] = field;
                        });

                        deferred.resolve(cache);
                    }, function(response) {
                        deferred.reject(response);
                    });
                } else {
                    deferred.resolve(cache);
                }

                return deferred.promise;
            }
        };
    });
})();
