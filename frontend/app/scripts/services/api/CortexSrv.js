(function() {
    'use strict';
    angular.module('theHiveServices').service('CortexSrv', function($q, $http, $rootScope, $uibModal, StatSrv, StreamSrv, AnalyzerSrv, PSearchSrv, ModalUtilsSrv) {
        var self = this;
        var baseUrl = './api/connector/cortex';

        this.list = function(scope, caseId, observableId, callback) {
            return PSearchSrv(undefined, 'connector/cortex/job', {
                scope: scope,
                sort: ['-startDate'],
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
        };

        this.getJobs = function(caseId, observableId, analyzerId, limit) {
            return $http.post(baseUrl + '/job/_search', {
                sort: ['-startDate'],
                range: '0-' + (
                limit || 10),
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
                                {
                                    analyzerId: analyzerId
                                }, {
                                    _like: {
                                        _field: 'analyzerDefinition',
                                        _value: analyzerId
                                    }
                                }
                            ]
                        }
                    ]
                }
            });
        };

        this.getJob = function(jobId, nstats) {
            if (nstats) {
                return $http.get(baseUrl + '/job/' + jobId, {
                    params: {
                        nstats: true
                    }
                });
            }
            return $http.get(baseUrl + '/job/' + jobId);

        };

        this.createJob = function(job) {
            return $http.post(baseUrl + '/job', job);
        };

        this.getServers = function(analyzerIds) {
            return AnalyzerSrv.serversFor(analyzerIds).then(function(servers) {
                if (servers.length === 1) {
                    return $q.resolve(servers[0]);
                } else {
                    return self.promptForInstance(servers);
                }
            });
        };

        this.promptForInstance = function(servers) {
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
        };

        this.getResponders = function(type, id) {
            //return $http.get(baseUrl + '/responder')
            return $http.get(baseUrl + '/responder/' + type + '/' + id)
              .then(function(response) {
                  return $q.resolve(response.data);
              })
              .catch(function(err) {
                  return $q.reject(err);
              });
        };

        this.runResponder = function(responderId, responderName, type, object) {
            var post = {
              responderId: responderId,
              objectType: type,
              objectId: object._id
            };

            return ModalUtilsSrv.confirm('Run responder ' + responderName, 'Are you sure you want to run responser ' + responderName + '?', {
                okText: 'Yes, run it'
            }).then(function() {
                return $http.post(baseUrl + '/action', post);
            });

        };
    });

})();
