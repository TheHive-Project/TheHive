(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('CaseSrv', function($resource) {
            return $resource('./api/case/:caseId', {}, {
                update: {
                    method: 'PATCH'
                },
                links: {
                    method: 'GET',
                    url: './api/case/:caseId/links',
                    isArray: true
                },
                merge: {
                    method: 'POST',
                    url: './api/case/:caseId/_merge/:mergedCaseId',
                    params: {
                        caseId: '@caseId',
                        mergedCaseId: '@mergedCaseId',
                    }
                },
                forceRemove: {
                    method: 'DELETE',
                    url : './api/case/:caseId/force',
                    params: {
                        caseId: '@caseId'
                    }
                },
                query: {
                    method: 'POST',
                    url: './api/case/_search',
                    isArray: true
                },
                alerts: {
                  method: 'POST',
                  url: './api/alert/_search',                  
                  isArray: true
                }
            });
        });
})();
