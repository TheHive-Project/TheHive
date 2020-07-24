(function() {
    'use strict';
    angular.module('theHiveServices').service('CortexSrv', function($q, $http, $rootScope, $uibModal, QuerySrv, StatSrv, StreamSrv, AnalyzerSrv, PSearchSrv, ModalUtilsSrv) {
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

            return QuerySrv.query('v1', [
                {
                    '_name': 'getObservable',
                    'idOrName': observableId
                },
                {
                    '_name': 'jobs'
                },
                {
                    '_name': 'filter',
                    '_or': [
                        {
                            'analyzerId': analyzerId
                        },
                        {
                            '_like': {
                                '_field': 'analyzerDefinition',
                                '_value': analyzerId
                            }
                        }
                    ]
                },
                {
                    '_name': 'sort',
                    '_fields': [
                        {
                            'startDate': 'desc'
                        }
                    ]
                },
                {
                    '_name': 'page',
                    'from': 0,
                    'to': limit || 10
                }
            ], {
                params: {
                    name: 'observable-jobs-' + observableId
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

        this.promntForResponder = function(responders) {
            if(!responders || responders.length ===0) {
                return $q.resolve('No responders available');
            }

            var modalInstance = $uibModal.open({
                animation: 'true',
                templateUrl: 'views/partials/misc/responder.selector.html',
                controller: 'ResponderSelectorCtrl',
                controllerAs: '$dialog',
                size: 'lg',
                resolve: {
                    responders: function() {
                        return responders;
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
