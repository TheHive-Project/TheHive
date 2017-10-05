(function() {
    'use strict';
    angular.module('theHiveServices').service('DashboardSrv', function(localStorageService, $q, AuthenticationSrv, $http) {
        var baseUrl = './api/dashboard';

        this.defaultDashboard = {
            items: [
                {
                    type: 'container',
                    items: []
                }
            ]
        };

        this.toolbox = [
            {
                id: 1,
                type: 'container',
                items: []
            },
            {
                id: 2,
                type: 'bar',
                options: {

                }
            },
            {
                id: 3,
                type: 'line',
                options: {

                }
            },
            {
                id: 4,
                type: 'donut',
                options: {
                    title: null,
                    entity: null,
                    field: null
                }
            }
        ];

        this.create = function(dashboard) {
            return $http.post(baseUrl, dashboard);
        }

        this.update = function(id, dashboard) {
            return $http.patch(baseUrl + '/' + id, dashboard);
        }

        this.list = function() {
            return $http.post(baseUrl + '/_search', {
                range: 'all',
                sort: ['-status', '-updatedAt', '-createdAt'],
                query: {
                    _and: [
                        {
                            _not: { status: 'Deleted' }
                        },
                        {
                            _or: [
                                { status: 'Shared' },
                                { createdBy: AuthenticationSrv.currentUser.id }
                            ]
                        }
                    ]
                }
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
