(function () {
    'use strict';
    angular.module('theHiveServices')
        .service('CaseTaskSrv', function ($resource, $http, $q, QuerySrv, ModalSrv) {
            var resource = $resource('./api/case/:caseId/task/:taskId', {}, {
                update: {
                    method: 'PATCH'
                }
            });

            this.get = resource.get;
            this.update = resource.update;
            this.query = resource.query;
            this.save = resource.save;

            this.getById = function (id) {
                var defer = $q.defer();

                QuerySrv.call('v1', [{
                    _name: 'getTask',
                    idOrName: id
                }], {
                    name: 'get-task-' + id,
                    page: {
                        from: 0,
                        to: 1,
                        extraData: ['actionRequired', 'actionRequiredMap']
                    }
                }).then(function (response) {
                    defer.resolve(response[0]);
                }).catch(function (err) {
                    defer.reject(err);
                });

                return defer.promise;
            };

            this.getActionRequiredMap = function (taskId) {
                return $http.get('./api/v1/task/' + taskId + '/actionRequired');
            };

            this.markAsDone = function (taskId, org) {
                return $http.put('./api/v1/task/' + taskId + '/actionDone/' + org);
            };

            this.markAsActionRequired = function (taskId, org) {
                return $http.put('./api/v1/task/' + taskId + '/actionRequired/' + org);
            };

            this.getShares = function (caseId, taskId) {
                return $http.get('./api/v1/case/' + caseId + '/task/' + taskId + '/shares');
            };

            this.addShares = function (taskId, organisations) {
                return $http.post('./api/case/task/' + taskId + '/shares', {
                    organisations: organisations
                });
            };

            this.bulkUpdate = function (ids, update) {
                return $http.patch('./api/v1/task/_bulk', _.extend({ ids: ids }, update));
            };

            this.removeShare = function (id, share) {
                return $http.delete('./api/task/' + id + '/shares', {
                    data: {
                        organisations: [share.organisationName]
                    },
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
            };

            this.promtForActionRequired = function (title, prompt) {
                var defer = $q.defer();

                var confirmModal = ModalSrv.confirm(
                    title,
                    prompt, {
                    okText: 'Yes, add log',
                    actions: [
                        {
                            flavor: 'default',
                            text: 'Proceed without log',
                            dismiss: 'skip-log'
                        }
                    ]
                }
                );

                confirmModal.result
                    .then(function (/*response*/) {
                        defer.resolve('add-log');
                    })
                    .catch(function (err) {
                        if (err === 'skip-log') {
                            defer.resolve(err);
                        } else {
                            defer.reject(err);
                        }
                    });

                return defer.promise;
            };

        });
})();
