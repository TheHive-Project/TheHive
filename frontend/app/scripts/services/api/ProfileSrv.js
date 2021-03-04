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
                        'managePlatform',
                        'manageUser',
                        'manageOrganisation',
                        'manageCustomField',
                        'manageConfig',
                        'manageTaxonomy',
                        'manageProfile',
                        'manageAnalyzerTemplate',
                        'manageObservableTemplate'
                    ],
                    labels: {
                        managePlatform: 'Manage the platform',
                        manageUser: 'Manage users',
                        manageOrganisation: 'Manage organisations',
                        manageCustomField: 'Manage custom fields',
                        manageConfig: 'Manage configurations',
                        manageTaxonomy: 'Manage taxonomies',
                        manageProfile: 'Manage profiles',
                        manageAnalyzerTemplate: 'Manage analyzer templates',
                        manageObservableTemplate: 'Manage observable types'
                    }
                },
                org: {
                    hints: 'Permissions for organisation user profiles',
                    keys: [
                        'manageUser',
                        'manageCaseTemplate',
                        'manageTag',
                        'manageAlert',
                        'manageCase',
                        'manageShare',
                        'manageObservable',
                        'manageTask',
                        'manageAction',
                        'manageAnalyse',
                        'accessTheHiveFS'
                    ],
                    labels: {
                        manageUser: 'Manage users',
                        manageCaseTemplate: 'Manage case templates',
                        manageTag: 'Manage custom tags',
                        manageAlert: 'Manage alert',
                        manageCase: 'Manage case',
                        manageShare: 'Manage sharing',
                        manageObservable: 'Manage observables',
                        manageTask: 'Manage tasks',
                        manageAction: 'Run Cortex responders',
                        manageAnalyse: 'Run Cortex analyzer',
                        accessTheHiveFS: 'Access to TheHiveFS service'
                    }
                }
            };

            this.list = function() {
                return $http.get(baseUrl, {params: {
                    range: 'all'
                }});
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
