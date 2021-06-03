(function () {
    'use strict';
    angular.module('theHiveServices')
        .service('CustomFieldsSrv', function ($http, $q) {
            var self = this;

            this.cache = null;

            this.removeField = function (field) {
                return $http.delete('./api/customField/' + field.id);
            };

            this.usage = function (field) {
                return $http.get('./api/customFields/' + field.id + '/use');
            };

            this.list = function () {
                return $http.get('./api/customField');
            };

            this.get = function (idOrReference) {
                return $http.get('./api/customField/' + idOrReference);
            };

            this.create = function (field) {
                //return $http.post('./api/customField', self._convert(field));
                return $http.post('./api/customField', field);
            };

            this.update = function (id, field) {
                //return $http.patch('./api/customField/'+id, self._convert(field));
                return $http.patch('./api/customField/' + id, field);
            };

            this.remove = function (id) {
                return $http.delete('./api/customField/' + id);
            };

            this.clearCache = function () {
                self.cache = null;
            };

            this.getCache = function (name) {
                return self.cache[name];
            };

            this.all = function () {
                var deferred = $q.defer();

                if (self.cache === null) {
                    $http.get('./api/customField')
                        .then(function (response) {
                            self.cache = {};

                            _.each(response.data, function (field) {
                                self.cache[field.reference] = field;
                            });

                            deferred.resolve(self.cache);
                        });
                } else {
                    deferred.resolve(self.cache);
                }

                return deferred.promise;
            };

        });
})();
