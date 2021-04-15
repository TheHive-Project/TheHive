(function () {
    'use strict';
    angular.module('theHiveServices')
        .service('ProfileSrv', function ($http) {
            var self = this;
            var baseUrl = './api/profile';

            this.adminProfile = 'admin';

            this.permissions = {
                admin: {
                    hints: 'Permissions for administration user profiles',
                    keys: [
                        'manageOrganisation',
                        'manageProfile',
                        'manageUser',
                        'manageCustomField',
                        'manageConfig',
                        'manageAnalyzerTemplate',
                        'manageObservableTemplate',
                        'manageTaxonomy',
                        'managePattern',
                        'managePlatform'
                    ],
                    labels: {
                        manageOrganisation: 'Manage organisations',
                        manageProfile: 'Manage profiles',
                        manageUser: 'Manage users',
                        manageCustomField: 'Manage custom fields',
                        manageConfig: 'Manage configurations',
                        manageAnalyzerTemplate: 'Manage analyzer templates',
                        manageObservableTemplate: 'Manage observable types',
                        manageTaxonomy: 'Manage taxonomies',
                        managePattern: 'Manage attack patterns',
                        managePlatform: 'Manage the platform'
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
                        'manageProcedure',
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
                        manageProcedure: 'Manage TTPs',
                        manageAction: 'Run Cortex responders',
                        manageAnalyse: 'Run Cortex analyzer',
                        accessTheHiveFS: 'Access to TheHiveFS service'
                    }
                }
            };

            this.list = function () {
                return $http.get(baseUrl, {
                    params: {
                        range: 'all'
                    }
                });
            };

            this.get = function (name) {
                return $http.get(baseUrl + '/' + name);
            };

            this.map = function () {
                return self.list()
                    .then(function (response) {
                        return _.indexBy(response.data, 'name');
                    });
            };

            this.create = function (profile) {
                return $http.post(baseUrl, profile);
            };

            this.update = function (id, profile) {
                return $http.patch(baseUrl + '/' + id, profile);
            };

            this.remove = function (id) {
                return $http.delete(baseUrl + '/' + id);
            };
        });

})();
