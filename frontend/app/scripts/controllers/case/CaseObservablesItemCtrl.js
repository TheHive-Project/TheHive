(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseObservablesItemCtrl',
        function ($scope, $state, $stateParams, $q, $filter, $timeout, $document, $uibModal, PaginatedQuerySrv, CaseSrv, ModalSrv, SecuritySrv, CaseTabsSrv, CaseArtifactSrv, CortexSrv, PSearchSrv, AnalyzerSrv, NotificationSrv, VersionSrv, TagSrv, appConfig, artifact) {
            var observableId = $stateParams.itemId,
                observableName = 'observable-' + observableId;

            $scope.caseId = $stateParams.caseId;
            $scope.report = null;
            $scope.obsResponders = null;
            $scope.analyzers = {};
            $scope.analyzerJobs = {};
            //$scope.jobs = {};
            $scope.state = {
                'editing': false,
                'isCollapsed': false,
                'dropdownOpen': false,
                'logMissing': ''
            };

            $scope.artifact = artifact;
            $scope.artifact.tlp = $scope.artifact.tlp !== undefined ? $scope.artifact.tlp : -1;
            $scope.analysisEnabled = VersionSrv.hasCortex();
            $scope.cortexServers = $scope.analysisEnabled && appConfig.connectors.cortex.servers;
            $scope.protectDownloadsWith = appConfig.config.protectDownloadsWith;
            $scope.similarArtifactsLimit = 10;

            $scope.editorOptions = {
                lineNumbers: true,
                theme: 'twilight',
                readOnly: 'nocursor',
                lineWrapping: true,
                mode: 'vb'
            };

            $scope.initScope = function (artifact) {

                var promise = $scope.analysisEnabled ? AnalyzerSrv.forDataType(artifact.dataType) : $q.resolve([]);

                promise
                    .then(function (analyzers) {
                        $scope.analyzers = analyzers;
                    }, function () {
                        $scope.analyzers = [];
                    })
                    .finally(function () {
                        if($scope.analysisEnabled) {
                            $scope.jobs = CortexSrv.listJobs($scope, $scope.caseId, observableId, $scope.onJobsChange);
                        }
                    });

                var connectors = $scope.appConfig.connectors;
                if(connectors.cortex && connectors.cortex.enabled) {
                    $scope.actions = new PaginatedQuerySrv({
                        name: 'case-observable-actions',
                        version: 'v1',
                        scope: $scope,
                        streamObjectType: 'action',
                        loadAll: true,
                        sort: ['-startDate'],
                        pageSize: 100,
                        operations: [
                            { '_name': 'getObservable', 'idOrName': artifact.id },
                            { '_name': 'actions' }
                        ],
                        guard: function(updates) {
                            return _.find(updates, function(item) {
                                return (item.base.details.objectType === 'Observable') && (item.base.details.objectId === artifact.id);
                            }) !== undefined;
                        }
                    });
                }
            };

            // Prepare the scope data
            $scope.initScope(artifact);

            $scope.scrollTo = function(hash) {
                $timeout(function() {
                    var el = angular.element(hash)[0];

                    // Scrolling hack using jQuery stuff
                    $('html,body').animate({
                        scrollTop: $(el).offset().top
                    }, 'fast');
                }, 100);
            };

            $scope.onJobsChange = function (updates) {
                $scope.analyzerJobs = {};

                _.each(_.keys($scope.analyzers).sort(), function(analyzerName) {
                    $scope.analyzerJobs[analyzerName] = [];
                });

                angular.forEach($scope.jobs.values, function (job) {
                    if (job.analyzerName in $scope.analyzerJobs) {
                        $scope.analyzerJobs[job.analyzerName].push(job);
                    } else {
                        $scope.analyzerJobs[job.analyzerName] = [job];
                    }
                });

                // Check it a job completed successfully and update the observableId
                if(updates && updates.length > 0) {

                    var statuses = _.pluck(_.map(updates, function(item) {
                        return item.base.details;
                    }), 'status');

                    if(statuses.indexOf('Success') > -1) {
                        CaseArtifactSrv.api().get({
                            'artifactId': observableId
                        }, function (observable) {
                            $scope.artifact = observable;
                        }, function (response) {
                            NotificationSrv.error('ObservableDetails', response.data, response.status);
                            CaseTabsSrv.activateTab('observables');
                        });
                    }
                }
            };

            $scope.showMoreSimilar = function() {
                $scope.similarArtifactsLimit = $scope.similarArtifactsLimit + 10;
            };

            $scope.refreshCurrentJob = function() {
                $scope.loadReport($scope.currentJob);
            };

            $scope.loadReport = function(jobId) {
                $scope.report = {};

                return CortexSrv.getJob(jobId, true)
                    .then(function(response) {
                        var job = response.data;
                        $scope.report = {
                            template: job.analyzerDefinition,
                            content: job.report,
                            status: job.status,
                            startDate: job.startDate,
                            endDate: job.endDate
                        };

                        $scope.currentJob = jobId;
                    });
            };

            $scope.showReport = function (jobId) {

                $scope.loadReport(jobId)
                    .then(function(){
                        $timeout(function() {
                            var reportEl = angular.element('#analysis-report')[0];

                            // Scrolling hack using jQuery stuff
                            $('html,body').animate({
                                scrollTop: $(reportEl).offset().top
                            }, 'fast');
                        }, 500);
                    })
                    .catch(function(/*err*/) {
                        NotificationSrv.error('An expected error occured while fetching the job report');
                    });

                // $scope.report = {};

                // CortexSrv.getJob(jobId, true).then(function(response) {
                //     var job = response.data;
                //     $scope.report = {
                //         template: job.analyzerDefinition,
                //         content: job.report,
                //         status: job.status,
                //         startDate: job.startDate,
                //         endDate: job.endDate
                //     };
                //
                //     $scope.currentJob = jobId;
                //
                //     $timeout(function() {
                //         var reportEl = angular.element('#analysis-report')[0];
                //
                //         // Scrolling hack using jQuery stuff
                //         $('html,body').animate({
                //             scrollTop: $(reportEl).offset().top
                //         }, 'fast');
                //     }, 500);
                //
                // }, function(/*err*/) {
                //     NotificationSrv.error('An expected error occured while fetching the job report');
                // });
            };

            $scope.openArtifact = function (a) {
                $state.go('app.case.observables-item', {
                    caseId: a.stats['case']._id,
                    itemId: a._id
                });
            };

            $scope.getLabels = function (selection) {
                var labels = [];

                angular.forEach(selection, function (label) {
                    labels.push(label.text);
                });

                return labels;
            };

            $scope.updateField = function (fieldName, newValue) {
                var field = {};
                field[fieldName] = newValue;

                return CaseArtifactSrv.api()
                    .update({
                        artifactId: $scope.artifact.id
                    }, field)
                    .$promise
                    .then(function () {
                        NotificationSrv.log('Observable has been updated', 'success');
                        return CaseArtifactSrv.api()
                            .get({
                                artifactId: $scope.artifact.id
                            }).$promise;
                    })
                    .then(function(response) {
                        $scope.artifact = response.toJSON();

                        if(fieldName === 'ignoreSimilarity' && !!!newValue) {
                            $scope.getSimilarity();
                        }
                    })
                    .catch(function (response) {
                        NotificationSrv.error('ObservableDetails', response.data, response.status);
                    });
            };

            $scope._runAnalyzer = function (serverId, analyzerId, artifactId) {
                return CortexSrv.createJob({
                    cortexId: serverId,
                    artifactId: artifactId,
                    analyzerId: analyzerId
                });
            };

            $scope.runAnalyzer = function (analyzerName, serverId) {
                var artifactName = $scope.artifact.data || $scope.artifact.attachment.name;

                var promise = serverId ? $q.resolve(serverId) : CortexSrv.getServers([analyzerName]);

                promise.then(function (serverId) {
                        return $scope._runAnalyzer(serverId, analyzerName, $scope.artifact._id);
                    })
                    .then(function () {
                        NotificationSrv.log('Analyzer ' + analyzerName + ' has been successfully started for observable: ' + artifactName, 'success');
                    }, function (response) {
                        if (response && response.status) {
                            NotificationSrv.log('Unable to run analyzer ' + analyzerName + ' for observable: ' + artifactName, 'error');
                        }
                    });
            };

            $scope.runAll = function () {
                var artifactId = $scope.artifact._id;
                var artifactName = $scope.artifact.data || $scope.artifact.attachment.name;
                var analyzerIds = _.pluck(_.filter($scope.analyzers, function (a) {
                    return a.active === true;
                }), 'name');

                CortexSrv.getServers(analyzerIds)
                    .then(function (serverId) {
                        return $q.all(_.map(analyzerIds, function (analyzerId) {
                            return $scope._runAnalyzer(serverId, analyzerId, artifactId);
                        }));
                    })
                    .then(function () {
                        NotificationSrv.log('Analyzers has been successfully started for observable: ' + artifactName, 'success');
                    });
            };

            $scope.getObsResponders = function(observable, force) {
                if(!force && $scope.obsResponders !== null) {
                   return;
                }

                $scope.obsResponders = null;
                CortexSrv.getResponders('case_artifact', observable._id)
                  .then(function(responders) {
                      $scope.obsResponders = responders;
                      return CortexSrv.promntForResponder(responders);
                  })
                  .then(function(response) {
                      if(response && _.isString(response)) {
                          NotificationSrv.log(response, 'warning');
                      } else {
                          return CortexSrv.runResponder(response.id, response.name, 'case_artifact', _.pick(observable, '_id'));
                      }
                  })
                  .then(function(response){
                      var data = '['+$filter('fang')(observable.data || observable.attachment.name)+']';
                      NotificationSrv.log(['Responder', response.data.responderName, 'started successfully on observable', data].join(' '), 'success');
                  })
                  .catch(function(err) {
                      if(err && !_.isString(err)) {
                          NotificationSrv.error('Observable Details', err.data, err.status);
                      }
                  });
            };

            $scope.getTags = function(query) {
                return TagSrv.autoComplete(query);
            };

            $scope.loadShares = function () {
                return CaseArtifactSrv.getShares($scope.caseId, observableId)
                    .then(function(response) {
                        $scope.shares = response.data;
                    });
            };

            $scope.removeShare = function(share) {
                var modalInstance = ModalSrv.confirm(
                    'Remove observable share',
                    'Are you sure you want to remove this sharing rule?', {
                        okText: 'Yes, remove it',
                        flavor: 'danger'
                    }
                );

                modalInstance.result
                    .then(function() {
                        return CaseArtifactSrv.removeShare($scope.artifact.id, share);
                    })
                    .then(function(/*response*/) {
                        $scope.loadShares();
                        NotificationSrv.log('Observable sharings updated successfully', 'success');
                    })
                    .catch(function(err) {
                        if(err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Observable sharings update failed', err.status);
                        }
                    });
            };

            $scope.addTaskShare = function() {
                var modalInstance = $uibModal.open({
                    animation: true,
                    templateUrl: 'views/components/sharing/sharing-modal.html',
                    controller: 'SharingModalCtrl',
                    controllerAs: '$modal',
                    size: 'lg',
                    resolve: {
                        shares: function() {
                            return CaseSrv.getShares($scope.caseId)
                                .then(function(response) {
                                    var caseShares = response.data;
                                    var taskShares = _.pluck($scope.shares, 'organisationName');

                                    var shares = _.filter(caseShares, function(item) {
                                        return taskShares.indexOf(item.organisationName) === -1;
                                    });

                                    return angular.copy(shares);
                                });
                        },

                    }
                });

                modalInstance.result
                    .then(function(orgs) {
                        return CaseArtifactSrv.addShares(observableId, orgs);
                    })
                    .then(function(/*response*/) {
                        $scope.loadShares();
                        NotificationSrv.log('Observable sharings updated successfully', 'success');
                    })
                    .catch(function(err) {
                        if(err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Observable sharings update failed', err.status);
                        }
                    });
            };

            $scope.getSimilarity = function() {
                CaseArtifactSrv.similar(observableId, {
                    range: 'all',
                    sort: ['-startDate']
                }).then(function(response) {
                    $scope.similarArtifacts = response;
                });
            };

            this.$onInit = function () {

                // Add tab
                CaseTabsSrv.addTab(observableName, {
                    name: observableName,
                    label: artifact.data || artifact.attachment.name,
                    closable: true,
                    state: 'app.case.observables-item',
                    params: {
                        itemId: artifact.id
                    }
                });

                // Select tab
                $timeout(function() {
                    CaseTabsSrv.activateTab(observableName);
                    $('html,body').animate({scrollTop: $('body').offset().top}, 'fast');
                }, 0);

                // Fetch similar cases
                if(!$scope.artifact.ignoreSimilarity) {
                    $scope.getSimilarity();
                }

                if(SecuritySrv.checkPermissions(['manageShare'], $scope.userPermissions)) {
                    $scope.loadShares();
                }
            };

        }
    );

})();
