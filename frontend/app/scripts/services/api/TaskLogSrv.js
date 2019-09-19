(function() {
    'use strict';
    angular.module('theHiveServices').factory('TaskLogSrv', function(FileResource) {
        return FileResource('./api/case/task/:taskId/log/:logId', {}, {
            update: {
                method: 'PATCH'
            }
        });
    });
})();
