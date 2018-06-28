(function() {
    'use strict';
    angular.module('theHiveServices').factory('CortexSrv', function($q, $http, $rootScope, $uibModal, StatSrv, StreamSrv, AnalyzerSrv, PSearchSrv) {

        var baseUrl = './api/connector/cortex';

        var factory = {
            list: function(scope, caseId, observableId, callback) {
                return PSearchSrv(undefined, 'connector/cortex/job', {
                    scope: scope,
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

            getJobs: function(caseId, observableId, analyzerId, limit) {
                return $http.post(baseUrl + '/job/_search', {
                    sort: '-startDate',
                    range: '0-' + (limit || 10),
                    query: {
                        _and: [
                            {
                                _parent: {
                                    _type: 'case_artifact',
                                    _query: {
                                        _id: observableId
                                    }
                                }
                            }, {
                                _or: [
                                  {analyzerId: analyzerId},
                                  {
                                    _like: {
                                      _field: 'analyzerDefinition',
                                      _value: analyzerId
                                    }
                                  }
                                ]
                            }
                        ]
                    }
                })
            },

            getJob: function(jobId, nstats) {
                if(nstats) {
                    return $http.get(baseUrl + '/job/' + jobId, {params: {nstats: true}});
                }
                return $http.get(baseUrl + '/job/' + jobId);

            },

            createJob: function(job) {
                return $http.post(baseUrl + '/job', job);
            },

            getServers: function(analyzerIds) {
                return AnalyzerSrv.serversFor(analyzerIds).then(function(servers) {
                    if (servers.length === 1) {
                        return $q.resolve(servers[0]);
                    } else {
                        return factory.promptForInstance(servers);
                    }
                });
            },

            promptForInstance: function(servers) {
                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/cortex/choose-instance-dialog.html',
                    controller: 'ServerInstanceDialogCtrl',
                    controllerAs: 'vm',
                    size: '',
                    resolve: {
                        servers: function() {
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
