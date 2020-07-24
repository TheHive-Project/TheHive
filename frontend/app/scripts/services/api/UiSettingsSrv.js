(function() {
    'use strict';
    angular.module('theHiveServices').factory('UiSettingsSrv', function($http, $q) {

        var settings = null;
        var baseUrl = './api/config/organisation/';

        var keys = [
            'ui.hideEmptyCaseButton'
        ];

        var factory = {
            keys: keys,
            clearCache: function() {
                settings = null;
            },

            get: function(name) {
                return settings[name];
            },

            save: function(name, value) {
                return $http.put(baseUrl + name, {value: value});
            },

            all: function(force) {
                var deferred = $q.defer();

                if(settings === null || force) {

                    settings = {};

                    $q.all(_.map(keys, function(key) {
                        return $http.get(baseUrl + key);
                    })).then(function(responses) {
                        _.each(responses, function(response) {
                            var data = response.data;

                            settings[data.path] = data;
                            settings[data.path].id = data.path;
                        });

                        deferred.resolve(settings);
                    }).catch(function(responses) {
                        deferred.reject(responses);
                    });
                } else {
                    deferred.resolve(settings);
                }

                return deferred.promise;
            }
        };

        keys.forEach(function(key) {
            var camelcased = s.camelize(key.replace(/\./gi, '_'));
            factory[camelcased] = function() {
                return (settings[key] || {}).value;
            };
        });

        return factory;
    });
})();
