(function () {
    'use strict';
    angular.module('theHiveServices')
        .factory('CortexSrv', function ($q, $http, $rootScope, $uibModal, StatSrv, StreamSrv, AnalyzerSrv, PSearchSrv) {

            var baseUrl = '/api/connector/cortex';

            var factory = {
                list: function (caseId, observableId, callback) {
                    return PSearchSrv(undefined, 'connector/cortex/job', {
                        sort: '-startDate',
                        loadAll: false,
                        pageSize: 200,
                        onUpdate: callback || angular.noop,
                        streamObjectType: 'case_artifact_job',
                        filter: {
                            _parent: {
                                _type: 'case_artifact',
                                _query: {
                                    _id: observableId
                                }
                            }
                        }
                    });
                },

                getJob: function (jobId) {
                    return $http.get(baseUrl + '/job/' + jobId);
                },

                createJob: function (job) {
                    return $http.post(baseUrl + '/job', job);
                },

                getServers: function (analyzerIds) {
                    return AnalyzerSrv.serversFor(analyzerIds)
                        .then(function (servers) {
                            if (servers.length === 1) {
                                return $q.resolve(servers[0]);
                            } else {
                                return factory.promptForInstance(servers);
                            }
                        });
                },

                promptForInstance: function (servers) {
                    var modalInstance = $uibModal.open({
                        templateUrl: 'views/partials/cortex/choose-instance-dialog.html',
                        controller: 'CortexInstanceDialogCtrl',
                        controllerAs: 'vm',
                        size: '',
                        resolve: {
                            servers: function () {
                                return servers;
                            }
                        }
                    });

                    return modalInstance.result;
                }
            };

            return factory;
        });

})();
