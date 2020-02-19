(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('ProfileSrv', function($http) {
            var self = this;
            var baseUrl = './api/profile';

            this.adminProfile = 'admin';

            this.permissions = {
                admin: {
                    hints: 'Permissions for administration user profiles',
                    keys: [
                        'manageUser',
                        'manageOrganisation',
                        'manageCustomField',
                        'manageConfig',
                        'manageTag',
                        'manageProfile',
                        'manageAnalyzerTemplate',
                        'manageObservableType'
                    ],
                    labels: {
                        manageUser: 'Manage users',
                        manageOrganisation: 'Manage organisations',
                        manageCustomField: 'Manage custom fields',
                        manageConfig: 'Manage configurations',
                        manageTag: 'Manage tags',
                        manageProfile: 'Manage profiles',
                        manageAnalyzerTemplate: 'Manage analyzer templates',
                        manageObservableType: 'Manage observable types'
                    }
                },
                org: {
                    hints: 'Permissions for organisation user profiles',
                    keys: [
                        'manageUser',
                        'manageCaseTemplate',
                        'manageAlert',
                        'manageCase',
                        'manageShare',
                        'manageObservable',
                        'manageTask',
                        'manageAction',
                        'manageAnalyse'
                    ],
                    labels: {
                        manageUser: 'Manage users',
                        manageCaseTemplate: 'Manage case templates',
                        manageAlert: 'Manage alert',
                        manageCase: 'Manage case',
                        manageShare: 'Manage sharing',
                        manageObservable: 'Manage observables',
                        manageTask: 'Manage tasks',
                        manageAction: 'Run Cortex responders',
                        manageAnalyse: 'Run Cortex analyzer'
                    }
                }
            };

            this.list = function() {
                return $http.get(baseUrl);
            };

            this.get = function(name) {
                return $http.get(baseUrl + '/' + name);
            };

            this.map = function() {
                return self.list()
                    .then(function(response) {
                        return _.indexBy(response.data, 'name');
                    });
            };

            this.create = function(profile) {
                return $http.post(baseUrl, profile);
            };

            this.update = function(id, profile) {
                return $http.patch(baseUrl + '/' + id, profile);
            };

            this.remove = function(id) {
                return $http.delete(baseUrl + '/' + id);
            };
        });

})();
