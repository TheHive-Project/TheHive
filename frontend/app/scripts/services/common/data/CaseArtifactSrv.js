(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('CaseArtifactSrv', function($http, FileResource) {
            var api = null;

            this.api = function() {
                if(api === null) {
                    return FileResource('./api/case/:caseId/artifact/:artifactId', {}, {
                        update: {
                            method: 'PATCH'
                        },
                        similar: {
                            url: './api/case/artifact/:artifactId/similar',
                            isArray: true
                        }
                    });
                }

                return api;
            };

            this.similar = function(observableId, options) {
                return $http.get('./api/case/artifact/'+observableId+'/similar', {
                        params: options
                    })
                    .then(function(response) {
                        return _.filter(response.data, function(item) {
                            return item.case;
                        });
                    });
            };

            this.bulkUpdate = function(ids, update) {
                return $http.patch('./api/case/artifact/_bulk', _.extend({ids: ids}, update));
            };

            this.getShares = function(caseId, observableId) {
                return $http.get('./api/case/' + caseId + '/observable/' + observableId + '/shares');
            };

            this.addShares = function(observableId, organisations) {
                return $http.post('./api/case/artifact/' + observableId + '/shares', {
                    organisations: organisations
                });
            };

            this.removeShare = function(id) {
                return $http.delete('./api/observable/shares', {
                    data: {
                        ids: [id]
                    },
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
            };

        });
})();
