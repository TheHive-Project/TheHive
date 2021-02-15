(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('ProcedureSrv', function($q, $http) {
            var self = this;
            var baseUrl = './api/v1/procedure';

            self.get = function(procedureId) {
                return $http.get(baseUrl + '/' + procedureId)
                    .then(function(response) {
                        return $q.resolve(response.data);
                    });
            };

            self.create = function(data) {
                return $http.post(baseUrl, data || {});
            };

            self.update = function(procedureId, updates) {
                return $http.patch(baseUrl + '/' + procedureId, updates);
            };

            self.remove = function(procedureId, updates) {
                return $http.delete(baseUrl + '/' + procedureId, updates);
            };

        });

})();
