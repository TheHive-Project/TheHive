(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseObservablesItemCtrl',
        function ($scope, $state, $stateParams, $q, CaseTabsSrv, CaseArtifactSrv, CortexSrv, PSearchSrv, AnalyzerSrv, JobSrv, AlertSrv, VersionSrv) {
            var observableId = $stateParams.itemId,
                observableName = 'observable-' + observableId;

            $scope.caseId = $stateParams.caseId;
            $scope.report = null;
            $scope.analyzers = {};
            $scope.analyzerJobs = {};
            $scope.jobs = {};
            $scope.state = {
                'editing': false,
                'isCollapsed': false,
                'dropdownOpen': false,
                'logMissing': ''
            };

            $scope.artifact = {};
            $scope.artifact.tlp = $scope.artifact.tlp || -1;
            $scope.analysisEnabled = VersionSrv.hasCortex();        

            $scope.editorOptions = {
                lineNumbers: true,
                theme: 'twilight',
                readOnly: 'nocursor',
                lineWrapping: true,
                mode: 'vb'
            };

            CaseArtifactSrv.api().get({
                'artifactId': observableId
            }, function (observable) {

                // Add tab
                CaseTabsSrv.addTab(observableName, {
                    name: observableName,
                    label: observable.data || observable.attachment.name,
                    closable: true,
                    state: 'app.case.observables-item',
                    params: {
                        itemId: observable.id
                    }
                });

                // Select tab
                CaseTabsSrv.activateTab(observableName);

                // Prepare the scope data
                $scope.initScope(observable);

            }, function (response) {
                AlertSrv.error('artifactDetails', response.data, response.status);
                CaseTabsSrv.activateTab('observables');
            });

            $scope.initScope = function (artifact) {
                $scope.artifact = artifact;

                // Get analyzers available for the observable's datatype
                AnalyzerSrv.forDataType(artifact.dataType)
                    .then(function (analyzers) {
                        $scope.analyzers = analyzers;
                    }, function () {
                        $scope.analyzers = [];
                    })
                    .finally(function () {
                        $scope.jobs = CortexSrv.list($scope.caseId, observableId, $scope.onJobsChange);
                    });

            };

            $scope.onJobsChange = function () {
                $scope.analyzerJobs = {};

                angular.forEach($scope.analyzers, function (analyzer, analyzerId) {
                    $scope.analyzerJobs[analyzerId] = [];
                });

                angular.forEach($scope.jobs.values, function (job) {
                    if (job.analyzerId in $scope.analyzerJobs) {
                        $scope.analyzerJobs[job.analyzerId].push(job);
                    } else {
                        $scope.analyzerJobs[job.analyzerId] = [job];

                        /*
                        AnalyzerSrv.get(job.analyzerId)
                            .finally(function (data) {
                                $scope.analyzers[data.analyzerId] = {
                                    active: false,
                                    showRows: false
                                };
                            });
                            */
                    }
                });
            };

            $scope.showReport = function (job) {
                $scope.report = {
                    template: job.analyzerId,
                    content: job.report,
                    status: job.status
                }
            }

            $scope.similarArtifacts = CaseArtifactSrv.api().similar({
                'artifactId': observableId
            });


            $scope.openArtifact = function (a) {
                $state.go('app.case.observables-item', {
                    caseId: a["case"].id,
                    itemId: a.id
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

                return CaseArtifactSrv.api().update({
                    artifactId: $scope.artifact.id
                }, field, function () {}, function (response) {
                    AlertSrv.error('artifactDetails', response.data, response.status);
                });
            };

            $scope._runAnalyzer = function (serverId, analyzerId, artifactId) {
                return CortexSrv.createJob({
                    cortexId: serverId,
                    artifactId: $scope.artifact.id,
                    analyzerId: analyzerId
                });
            }

            $scope.runAnalyzer = function (analyzerId) {
                var artifactName = $scope.artifact.data || $scope.artifact.attachment.name;

                CortexSrv.getServers([analyzerId])
                    .then(function (serverId) {
                        return $scope._runAnalyzer(serverId, analyzerId, $scope.artifact.id);
                    })
                    .then(function () {
                        AlertSrv.log('Analyzer ' + analyzerId + ' has been successfully started for observable: ' + artifactName, 'success');
                    }, function (response) {
                        if (response && response.status) {
                            AlertSrv.log('Unable to run analyzer ' + analyzerId + ' for observable: ' + artifactName, 'error');
                        }
                    });
            };

            $scope.runAll = function () {
                var artifactId = $scope.artifact.id;
                var artifactName = $scope.artifact.data || $scope.artifact.attachment.name;
                var analyzerIds = _.pluck(_.filter($scope.analyzers, function (a) {
                    return a.active = true;
                }), 'id');

                CortexSrv.getServers(analyzerIds)
                    .then(function (serverId) {
                        return $q.all(_.map(analyzerIds, function (analyzerId) {
                            return $scope._runAnalyzer(serverId, analyzerId, artifactId);
                        }));
                    })
                    .then(function () {
                        AlertSrv.log('Analyzers has been successfully started for observable: ' + artifactName, 'success');
                    }, function () {

                    })
            };

        }
    );

})();
