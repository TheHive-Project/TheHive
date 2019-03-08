(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('CaseArtifactSrv', function($http, FileResource) {
            var api = null;

            var factory = {
                api: function() {
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
                },

                bulkUpdate: function(ids, update) {
                    return $http.patch('./api/case/artifact/_bulk', _.extend({ids: ids}, update));
                }
            };

            return factory;

        });
})();
