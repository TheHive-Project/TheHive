(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('CaseTaskSrv', function($resource, $http, $q, QuerySrv) {
            var resource = $resource('./api/case/:caseId/task/:taskId', {}, {
                update: {
                    method: 'PATCH'
                }
            });

            this.get = resource.get;
            this.update = resource.update;
            this.query = resource.query;
            this.save = resource.save;

            this.getById = function(id) {
                var defer = $q.defer();

                QuerySrv.call('v1', [{
                    '_name': 'getTask',
                    'idOrName': id
                }], {
                    name: 'get-task-' + id,
                    page: {
                        from: 0,
                        to: 1
                    }
                }).then(function(response) {
                    defer.resolve(response[0]);
                }).catch(function(err){
                    defer.reject(err);
                });

                return defer.promise;
            };

            this.getShares = function(caseId, taskId) {
                return $http.get('./api/case/' + caseId + '/task/' + taskId + '/shares');
            };

            this.addShares = function(taskId, organisations) {
                return $http.post('./api/case/task/' + taskId + '/shares', {
                    organisations: organisations
                });
            };

            this.removeShare = function(id, share) {
                return $http.delete('./api/task/'+id+'/shares', {
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
