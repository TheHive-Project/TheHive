(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('ProfileSrv', function($http) {
            var self = this;
            var baseUrl = './api/profile';

            this.permissions = {
                keys: [
                    'manageUser',
                    'manageCaseTemplate',
                    'manageAlert',
                    'manageCase',
                    'manageShare',
                    'manageObservable',
                    'manageTask',
                    'manageAction',
                    'manageOrganisation',
                    'manageCustomField',
                    'manageConfig',
                    'manageTag',
                    'manageProfile',
                    'manageReportTemplate'
                ],
                labels: {
                    manageUser: 'Manage users',
                    manageCaseTemplate: 'Manage case templates',
                    manageAlert: 'Manage alert',
                    manageCase: 'Manage case',
                    manageShare: 'Manage sharing',
                    manageObservable: 'Manage observables',
                    manageTask: 'Manage tasks',
                    manageAction: 'Manage actions',
                    manageOrganisation: 'Manage organisations',
                    manageCustomField: 'Manage csutom fields',
                    manageConfig: 'Manage configurations',
                    manageTag: 'Manage tags',
                    manageProfile: 'Manage profiles',
                    manageReportTemplate: 'Manage report templates'
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
