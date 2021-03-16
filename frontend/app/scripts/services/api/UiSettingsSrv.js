(function() {
    'use strict';
    angular.module('theHiveServices').service('UiSettingsSrv', function($http, $q) {
        var baseUrl = './api/config/organisation/';
        var self = this;

        this.settings = null;

        this.keys = [
            'hideEmptyCaseButton',
            'disallowMergeAlertInResolvedSimilarCases',
            'defaultAlertSimilarCaseFilter',
            'defaultDateFormat'
        ];

        this.clearCache = function() {
            self.settings = null;
        };

        this.get = function(name) {
            return self.settings[name];
        };

        this.save = function(name, value) {
            return $http.put(baseUrl + 'ui.' + name, {value: value});
        };

        this.all = function(force) {
            var deferred = $q.defer();

            if(self.settings === null || force) {

                self.settings = {};

                $http.get('./api/config/organisation?path=ui')
                    .then(function(response) {
                        var data = response.data;

                        self.settings = data;
                        deferred.resolve(data);
                    })
                    .catch(function(response) {
                        deferred.reject(response);
                    });
            } else {
                deferred.resolve(self.settings);
            }

            return deferred.promise;
        };

        this.keys.forEach(function(key) {
            var camelcased = s.camelize(key.replace(/\./gi, '_'));
            self[camelcased] = function() {
                return (self.settings || {})[key];
            };
        });
    });
})();
