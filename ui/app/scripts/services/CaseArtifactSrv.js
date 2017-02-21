(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('CaseArtifactSrv', function(FileResource) {
            var api = null;
            var filters = null;

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
                }
            };

            return factory;

        });
})();
