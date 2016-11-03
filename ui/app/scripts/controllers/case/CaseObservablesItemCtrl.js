(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseObservablesItemCtrl',
        function($scope, $state, $stateParams, CaseTabsSrv, CaseArtifactSrv, PSearchSrv, AnalyzerSrv, AnalyzerInfoSrv, JobSrv, AlertSrv) {
            var observableId = $stateParams.itemId,
                observableName = 'observable-' + observableId;

            $scope.caseId = $stateParams.caseId;
            $scope.getAnalyzerInfo = AnalyzerInfoSrv;
            $scope.report = {};
            $scope.analyzers = {};
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
            }, function(data) {
                var observable = data.artifact;

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
                $scope.initScope(data);

                // Prepare the jobs data
                $scope.initJobs();

            }, function(response) {
                AlertSrv.error('artifactDetails', response.data, response.status);
                CaseTabsSrv.activateTab('observables');
            });

            $scope.initScope = function(data) {
                $scope.artifactAnalyzers = data.analyzers;
                $scope.artifact = data.artifact;

                angular.forEach($scope.artifactAnalyzers, function(analyzer) {
                    analyzer.active = true;
                    $scope.analyzers[analyzer.id] = analyzer;
                });
            };

            $scope.similarArtifacts = CaseArtifactSrv.api().similar({
                'artifactId': observableId
            });

            $scope.initJobs = function() {
                var jobs = PSearchSrv($scope.caseId, 'case_artifact_job', {
                    'filter': {
                        '_parent': {
                            '_type': 'case_artifact',
                            '_query': {
                                '_id': $scope.artifact.id
                            }
                        }
                    },
                    'pageSize': 200,
                    'sort': '-startDate',
                    'onUpdate': function() {
                        $scope.analyzerJobs = {};
                        angular.forEach($scope.analyzers, function(analyzer, analyzerId) {
                            $scope.analyzerJobs[analyzerId] = [];
                        });
                        angular.forEach(jobs.values, function(job) {
                            if (job.analyzerId in $scope.analyzerJobs) {
                                $scope.analyzerJobs[job.analyzerId].push(job);
                            } else {
                                $scope.analyzerJobs[job.analyzerId] = [job];

                                console.log('AnalyzerSrv.get(' + job.analyzerId + ')');

                                AnalyzerSrv.get({
                                    'analyzerId': job.analyzerId
                                }, function(data) {
                                    $scope.analyzers[data.analyzerId] = {
                                        active: false,
                                        showRows: false
                                    };
                                }, function(response) {
                                    AlertSrv.error('artifactDetails', response.data, response.status);
                                });
                            }
                        });
                    }
                });
            };

            $scope.openArtifact = function(a) {
                $state.go('app.case.observables-item', {
                    caseId: a["case"].id,
                    itemId: a.id
                });
            };

            $scope.getLabels = function(selection) {
                var labels = [];

                angular.forEach(selection, function(label) {
                    labels.push(label.text);
                });

                return labels;
            };

            $scope.updateField = function(fieldName, newValue) {
                var field = {};
                field[fieldName] = newValue;
                console.log('update artifact field ' + fieldName + ':' + field[fieldName]);
                return CaseArtifactSrv.api().update({
                    artifactId: $scope.artifact.id
                }, field, function() {}, function(response) {
                    AlertSrv.error('artifactDetails', response.data, response.status);
                });
            };

            $scope.runAnalyzer = function(analyzerId) {
                console.log('running analyzer ' + analyzerId + ' on artifact ' + $scope.artifact.id);
                return JobSrv.save({
                    'artifactId': $scope.artifact.id
                }, {
                    'analyzerId': analyzerId
                }, function() {}, function(response) {
                    AlertSrv.error('artifactDetails', response.data, response.status);
                });
            };

        }
    );

})();
