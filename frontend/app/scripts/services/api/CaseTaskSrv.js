(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('CaseTaskSrv', function($resource, $http) {
            var resource = $resource('./api/case/:caseId/task/:taskId', {}, {
                update: {
                    method: 'PATCH'
                }
            });

            this.get = resource.get;
            this.update = resource.update;
            this.query = resource.query;
            this.save = resource.save;

            this.getShares = function(caseId, taskId) {
                return $http.get('./api/case/' + caseId + '/task/' + taskId + '/shares');
            };

            this.addShares = function(taskId, organisations) {
                return $http.post('./api/case/task/' + taskId + '/shares', {
                    organisations: organisations
                });
            };

            this.removeShare = function(id) {
                return $http.delete('./api/task/shares', {
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
