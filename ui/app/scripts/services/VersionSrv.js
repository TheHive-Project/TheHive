(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('VersionSrv', function($http, $q) {
            var cache = null;

            return {
                get: function() {
                    var deferred = $q.defer();

                    if(cache !== null) {
                        deferred.resolve(cache);
                    } else {
                        $http.get('/api/status').then(function(response) {
                            cache = response.data;
                            deferred.resolve(cache);
                        }, function(rejection) {
                            deferred.reject(rejection);
                        });
                    }

                    return deferred.promise;
                }
            };
        });
})();
