(function() {
    'use strict';
    angular.module('theHiveServices').service('DashboardSrv', function(localStorageService, $q, $http) {
        var baseUrl = './api/dashboard';

        this.defaultDashboard = {
            items: [
                {
                    type: 'container',
                    items: []
                }
            ]
        };

        this.create = function(dashboard) {
            return $http.post(baseUrl, dashboard);
        }

        this.update = function(id, dashboard) {
            return $http.patch(baseUrl + '/' + id, dashboard);
        }

        this.list = function() {
            return $http.post(baseUrl + '/_search', {
                range: 'all',
                query: {}
            });
        }

        this.get = function(id) {
            return $http.get(baseUrl + '/' + id);
        }

        this.remove = function(id) {
            return $http.delete(baseUrl + '/' + id);
        }

    });
})();
