(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('CaseTaskSrv', function($resource) {
            return $resource('./api/case/:caseId/task/:taskId', {}, {
                update: {
                    method: 'PATCH'
                }
            });
        });
})();
