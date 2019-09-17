(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('OrganisationSrv', function($q, $http) {

            var baseUrl = './api/organisation';

            var factory = {

                list: function() {
                    return $http.get(baseUrl);
                },

                get: function(orgId) {
                    return $http.get(baseUrl + '/' + orgId)
                        .then(function(response) {
                            return $q.resolve(response.data);
                        });
                },

                create: function(data) {
                    return $http.post(baseUrl + '/', data || {});
                },

                update: function(orgId, updates) {
                    return $http.patch(baseUrl + '/' + orgId, updates);
                },

                users: function(orgId) {

                }

            };

            return factory;
        });

})();
