(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseObservablesItemCtrl',
        function ($scope, $state, $stateParams, CaseTabsSrv, CaseArtifactSrv, CortexSrv, PSearchSrv, AnalyzerSrv, JobSrv, AlertSrv) {
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
                        return $scope.analyzers = analyzers;
                    })
                    .then(function (analyzers) {
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

                        AnalyzerSrv.get(job.analyzerId)
                            .finally(function (data) {
                                    $scope.analyzers[data.analyzerId] = {
                                        active: false,
                                        showRows: false
                                    };
                                });
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
                console.log('update artifact field ' + fieldName + ':' + field[fieldName]);
                return CaseArtifactSrv.api().update({
                    artifactId: $scope.artifact.id
                }, field, function () {}, function (response) {
                    AlertSrv.error('artifactDetails', response.data, response.status);
                });
            };

            $scope.runAnalyzer = function (analyzerId) {
                console.log('running analyzer ' + analyzerId + ' on artifact ' + $scope.artifact.id);
                return CortexSrv.createJob({
                    cortexId: 'local',
                    artifactId: $scope.artifact.id,
                    analyzerId: analyzerId
                }, function () {}, function (response) {
                    AlertSrv.error('artifactDetails', response.data, response.status);
                });
            };

        }
    );

})();
