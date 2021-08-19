(function () {
    'use strict';
    angular.module('theHiveServices')
        .service('OrganisationSrv', function ($q, $http, QuerySrv) {
            var self = this;
            var baseUrl = './api/v1/organisation';

            self.defaultOrg = 'admin';

            self.isDefaultOrg = function (org) {
                return org.name === self.defaultOrg;
            };

            self.list = function () {
                return $http.get('./api/organisation');
            };

            self.links = function (orgId) {
                return $http.get(baseUrl + '/' + orgId + '/links')
                    .then(function (response) {
                        return $q.resolve(response.data);
                    });
            };

            self.setLinks = function (orgId, links) {

                var promises = _.map(links, function (link) {
                    return $http.put(
                        baseUrl + '/' + orgId + '/link/' + link.toOrganisation,
                        _.pick(link, 'linkType', 'otherLinkType')
                    )
                })


                return $q.all(promises)
                    .then(function (responses) {
                        return $q.resolve(responses);
                    });

                // return $http.put(baseUrl + '/' + orgId + '/links', {
                //     organisations: links || []
                // })
                //     .then(function(response) {
                //         return $q.resolve(response.data);
                //     });
            };

            self.get = function (orgId) {
                return $http.get(baseUrl + '/' + orgId)
                    .then(function (response) {
                        return $q.resolve(response.data);
                    });
            };

            self.create = function (data) {
                return $http.post(baseUrl + '/', data || {});
            };

            self.update = function (orgId, updates) {
                return $http.patch(baseUrl + '/' + orgId, updates);
            };

            self.users = function (orgId) {
                return QuerySrv.query('v1', [{
                    '_name': 'getOrganisation',
                    'idOrName': orgId
                },
                {
                    '_name': 'users'
                }
                ], {
                    params: {
                        name: 'users'
                    }
                }).then(function (response) {
                    return $q.resolve(response.data);
                });
            };

            self.caseTemplates = function (orgId) {
                return QuerySrv.query('v0', [{
                    '_name': 'getOrganisation',
                    'idOrName': orgId
                },
                {
                    '_name': 'caseTemplates'
                }
                ], {
                    params: {
                        name: 'caseTemplates'
                    }
                }).then(function (response) {
                    return $q.resolve(response.data);
                });
            };
        });

})();
