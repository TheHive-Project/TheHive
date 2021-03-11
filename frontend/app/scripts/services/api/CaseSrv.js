(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('CaseSrv', function($q, $http, $resource, QuerySrv) {

            var resource = $resource('./api/case/:caseId', {}, {
                update: {
                    method: 'PATCH'
                },
                links: {
                    method: 'GET',
                    url: './api/case/:caseId/links',
                    isArray: true
                },
                // merge: {
                //     method: 'POST',
                //     url: './api/case/:caseId/_merge/:mergedCaseId',
                //     params: {
                //         caseId: '@caseId',
                //         mergedCaseId: '@mergedCaseId',
                //     }
                // },
                forceRemove: {
                    method: 'DELETE',
                    url: './api/case/:caseId/force',
                    params: {
                        caseId: '@caseId'
                    }
                },
                query: {
                    method: 'POST',
                    url: './api/case/_search',
                    isArray: true
                },
                // alerts: {
                //     method: 'POST',
                //     url: './api/alert/_search',
                //     isArray: true
                // }
            });

            this.get = resource.get;
            this.alerts = resource.alerts;
            this.save = resource.save;
            this.forceRemove = resource.forceRemove;
            this.links = resource.links;
            this.update = resource.update;
            this.merge = resource.merge;
            this.query = resource.query;

            this.alerts = function(id) {
                var defer = $q.defer();

                QuerySrv.call('v1', [{
                    '_name': 'getCase',
                    'idOrName': id
                }, {'_name': 'alerts'}], {
                    name:'get-case-alerts' + id
                }).then(function(response) {
                    defer.resolve(response);
                }).catch(function(err){
                    defer.reject(err);
                });

                return defer.promise;
            };

            this.getById = function(id, withStats) {
                var defer = $q.defer();

                QuerySrv.call('v1', [{
                    '_name': 'getCase',
                    'idOrName': id
                }], {
                    name:'get-case-' + id,
                    page: {
                        from: 0,
                        to: 1,
                        extraData: withStats ? [
                            "observableStats",
                            "taskStats",
                            "alerts",
                            "isOwner",
                            "shareCount",
                            "permissions"
                        ] : []
                    }
                }).then(function(response) {
                    defer.resolve(response[0]);
                }).catch(function(err){
                    defer.reject(err);
                });

                return defer.promise;
            };

            this.merge = function(ids) {
                return $http.post('./api/v1/case/_merge/' + ids.join(','));
            };

            this.bulkUpdate = function(ids, update) {
                return $http.patch('./api/case/_bulk', _.extend({ids: ids}, update));
            };

            this.getShares = function(id) {
                return $http.get('./api/case/' + id + '/shares');
            };

            this.setShares = function(id, shares) {
                return $http.post('./api/case/' + id + '/shares', {
                    "shares": shares
                });
            };

            this.updateShare = function(org, patch) {
                return $http.patch('./api/case/share/' + org, patch);
            };

            this.removeShare = function(id, share) {
                return $http.delete('./api/case/'+id+'/shares', {
                    data: {
                        organisations: [share.organisationName]
                    },
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
            };
        });

})();
