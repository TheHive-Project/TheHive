(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('VersionSrv', function($http, $q) {
            var cache = null;

            var factory =  {
                get: function() {
                    var deferred = $q.defer();

                    if(cache !== null) {
                        deferred.resolve(cache);
                    } else {
                        $http.get('./api/status').then(function(response) {
                            cache = response.data;
                            deferred.resolve(cache);
                        }, function(rejection) {
                            deferred.reject(rejection);
                        });
                    }

                    return deferred.promise;
                },

                hasCortex: function() {
                    try {
                        var service = cache.connectors.cortex;
                        
                        return service.enabled && service.servers.length;
                    } catch (err) {
                        return false;
                    }
                }
            };

            return factory;
        });
})();
