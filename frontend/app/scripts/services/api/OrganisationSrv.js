(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('OrganisationSrv', function($q, $http, QuerySrv) {

            var baseUrl = './api/organisation';

            var factory = {
                defaultOrg: 'default',

                list: function() {
                    return $http.get(baseUrl);
                },

                links: function(orgId) {
                    return $http.get(baseUrl + '/' + orgId + '/links')
                        .then(function(response) {
                            return $q.resolve(response.data);
                        });
                },

                setLinks: function(orgId, links) {
                    return $http.put(baseUrl + '/' + orgId + '/links', links)
                        .then(function(response) {
                            return $q.resolve(response.data);
                        });
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
                    return QuerySrv.query('v1', [{
                            '_name': 'getOrganisation',
                            'idOrName': orgId
                        },
                        {
                            '_name': 'users'
                        },
                        {
                            '_name': 'toList'
                        }
                    ], {
                        headers: {
                            'X-Organisation': orgId
                        }
                    }).then(function(response) {
                        return $q.resolve(response.data.result);
                    });
                },

                caseTemplates: function(orgId) {
                    return QuerySrv.query('v0', [{
                            '_name': 'getOrganisation',
                            'idOrName': orgId
                        },
                        {
                            '_name': 'caseTemplates'
                        },
                        {
                            '_name': 'toList'
                        }
                    ]).then(function(response) {
                        return $q.resolve(response.data.result);
                    });
                }
            };

            return factory;
        });

})();
