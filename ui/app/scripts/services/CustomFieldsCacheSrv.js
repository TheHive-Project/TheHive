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
                        var json = response.toJSON();
                        cache = {};

                        _.each(_.values(json), function(field) {
                            cache[field.reference] = field;
                        })

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
