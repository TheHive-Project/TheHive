(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('VersionSrv', function($http, $q, $interval) {
            var cache = null;

            var factory =  {
                startMonitoring: function(callback) {
                    $interval(function() {
                        factory.get(true)
                          .then(function(appConfig) {
                              if(callback) {
                                  callback(appConfig);
                              }
                          });
                    }, 60000);
                },
                get: function(force) {
                    var deferred = $q.defer();

                    if(!force && cache !== null) {
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

                hasCortexConnector: function() {
                    try {
                        var service = cache.connectors.cortex;

                        return service.enabled;
                    } catch (err) {
                        return false;
                    }
                },

                hasCortex: function() {
                    try {
                        var service = cache.connectors.cortex;

                        return service.enabled && _.pluck(service.servers, 'status').indexOf('OK') !== -1;
                    } catch (err) {
                        return false;
                    }
                },

                mispUrls: function() {
                    var urls = {};
                    var misp = cache.connectors.misp;

                    if(!misp) {
                        return {};
                    }

                    (misp.servers || []).forEach(function(item) {
                        urls[item.name] = item.url;
                    });
                    return urls;
                }
            };

            return factory;
        });
})();
