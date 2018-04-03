(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseObservablesCtrl',
        function ($scope, $q, $state, $stateParams, $uibModal, StreamSrv, CaseTabsSrv, PSearchSrv, CaseArtifactSrv, NotificationSrv, AnalyzerSrv, CortexSrv, ObservablesUISrv, VersionSrv, Tlp) {

            CaseTabsSrv.activateTab($state.current.data.tab);

            $scope.analysisEnabled = VersionSrv.hasCortex();
            $scope.uiSrv = ObservablesUISrv;
            $scope.caseId = $stateParams.caseId;
            $scope.showText = false;

            $scope.uiSrv.initContext($scope.caseId);
            $scope.searchForm = {
                searchQuery: $scope.uiSrv.buildQuery() || ''
            };
            $scope.selection = {};

            $scope.artifacts = PSearchSrv($scope.caseId, 'case_artifact', {
                scope: $scope,
                baseFilter: {
                    '_and': [{
                        '_parent': {
                            "_type": "case",
                            "_query": {
                                "_id": $scope.caseId
                            }
                        }
                    },   {
                        'status': 'Ok'
                    }]
                },
                filter: $scope.searchForm.searchQuery !== '' ? {
                    _string: $scope.searchForm.searchQuery
                } : '',
                loadAll: true,
                sort: '-startDate',
                pageSize: $scope.uiSrv.context.pageSize,
                onUpdate: function () {
                    $scope.updateSelection();
                },
                nstats: true
            });

            // Add a listener to refresh observables list on job finish
            StreamSrv.addListener({
                scope: $scope,
                rootId: $scope.caseId,
                objectType: 'case_artifact_job',
                callback: function(data) {
                    var successFound = false;
                    var i = 0;
                    var ln = data.length;

                    while(!successFound && i < ln) {
                        if(data[i].base.operation === 'Update' && data[i].base.details.status === 'Success') {
                            successFound = true;
                        }
                        i++;
                    }

                    if(successFound) {
                        $scope.artifacts.update();
                    }
                }
            });

            $scope.$watchCollection('artifacts.pageSize', function (newValue) {
                $scope.uiSrv.setPageSize(newValue);
            });

            $scope.keys = function(obj) {
                return _.keys(obj || {});
            };

            $scope.toggleStats = function () {
                $scope.uiSrv.toggleStats();
            };

            $scope.toggleFilters = function () {
                $scope.uiSrv.toggleFilters();
            };

            $scope.filter = function () {
                $scope.uiSrv.filter().then($scope.applyFilters);
            };

            $scope.applyFilters = function () {
                $scope.searchForm.searchQuery = $scope.uiSrv.buildQuery();
                $scope.search();
            };

            $scope.clearFilters = function () {
                $scope.uiSrv.clearFilters($scope.caseId).then($scope.applyFilters);
            };

            $scope.addFilter = function (field, value) {
                $scope.uiSrv.addFilter(field, value).then($scope.applyFilters);
            };

            $scope.removeFilter = function (field) {
                $scope.uiSrv.removeFilter(field).then($scope.applyFilters);
            };

            $scope.search = function () {
                $scope.artifacts.filter = {
                    _string: $scope.searchForm.searchQuery
                };

                $scope.artifacts.update();
            };
            $scope.addFilterValue = function (field, value) {
                var filterDef = $scope.uiSrv.filterDefs[field];
                var filter = $scope.uiSrv.activeFilters[field];
                var date;

                if (filter && filter.value) {
                    if (filterDef.type === 'list') {
                        if (_.pluck(filter.value, 'text').indexOf(value) === -1) {
                            filter.value.push({
                                text: value
                            });
                        }
                    } else if (filterDef.type === 'date') {
                        date = moment(value);
                        $scope.uiSrv.activeFilters[field] = {
                            value: {
                                from: date.hour(0).minutes(0).seconds(0).toDate(),
                                to: date.hour(23).minutes(59).seconds(59).toDate()
                            }
                        };
                    } else {
                        filter.value = value;
                    }
                } else {
                    if (filterDef.type === 'list') {
                        $scope.uiSrv.activeFilters[field] = {
                            value: [{
                                text: value
                            }]
                        };
                    } else if (filterDef.type === 'date') {
                        date = moment(value);
                        $scope.uiSrv.activeFilters[field] = {
                            value: {
                                from: date.hour(0).minutes(0).seconds(0).toDate(),
                                to: date.hour(23).minutes(59).seconds(59).toDate()
                            }
                        };
                    } else {
                        $scope.uiSrv.activeFilters[field] = {
                            value: value
                        };
                    }
                }

                $scope.filter();
            };

            $scope.filterByTlp = function(value) {
                $scope.addFilterValue('tlp', Tlp.values[value]);
            };

            $scope.countReports = function(observable) {
                return _.keys(observable.reports).length;
            };

            // FIXME à quoi ça sert ? c'est un tableau ou un object ?
            $scope.artifactList = [];
            $scope.artifactList.Action = 'main';
            $scope.artifactList.isCollapsed = true;
            $scope.artifactList.ttags = [];
            $scope.analyzersList = {
                active: {},
                datatypes: {}
            };
            $scope.selection = {};

            //
            // init lists
            //
            $scope.initSelection = function (selection) {
                selection.all = false;
                selection.list = {};
                selection.artifacts = [];
                selection.Action = 'main';
                selection.isCollapsed = true;
                angular.forEach($scope.artifacts.allValues, function (artifact) {
                    selection.list[artifact.id] = false;
                });
            };

            $scope.initAnalyzersList = function () {
                AnalyzerSrv.query()
                    .then(function (analyzers) {
                        $scope.analyzersList.analyzers = analyzers;
                        $scope.analyzersList.active = {};
                        $scope.analyzersList.datatypes = {};
                        angular.forEach($scope.analyzersList.analyzers, function (analyzer) {
                            $scope.analyzersList.active[analyzer.name] = false;
                        });
                        $scope.analyzersList.selected = {};
                        angular.forEach($scope.analyzersList.analyzers, function (analyzer) {
                            $scope.analyzersList.selected[analyzer.name] = false;
                        });
                    });
            };

            $scope.initSelection($scope.selection);
            $scope.initAnalyzersList();

            //
            // update selection of artifacts each time artifacts is updated
            $scope.updateSelection = function () {
                if ($scope.selection.all) {
                    $scope.selection.list = {};

                    $scope.selection.artifacts = $scope.artifacts.allValues;
                    angular.forEach($scope.artifacts.allValues, function (element) {
                        $scope.selection.list[element.id] = true;
                    });
                } else {
                    var lid = _.pluck($scope.artifacts.allValues, 'id');

                    $scope.selection.artifacts.length = 0;
                    angular.forEach($scope.selection.list, function (value, key) {
                        var index = lid.indexOf(key);
                        if (index >= 0) {
                            if (value) {
                                $scope.selection.artifacts.push($scope.artifacts.allValues[index]);
                            }
                        } else {
                            delete $scope.selection.list[key];
                        }
                    });

                }
            };

            // check if an artifact in in artifacts psearch list
            $scope.isInArtifacts = function (artifact) {
                angular.every($scope.artifacts.allValues, function (element) {
                    return angular.equals(artifact.id, element.id);
                });
            };

            // select all artifacts : add all artifacts in selection or delete selection
            $scope.selectAll = function () {
                if ($scope.selection.all) {
                    $scope.selection.artifacts = $scope.artifacts.allValues.slice();
                    angular.forEach($scope.artifacts.allValues, function (element) {
                        $scope.selection.list[element.id] = true;
                        $scope.incDataType(element.dataType);
                    });
                } else {
                    $scope.initSelection($scope.selection);
                    $scope.initAnalyzersList();
                }
                $scope.activeAnalyzers();
            };

            // control if an artifact is selected or not
            $scope.artifactIsSelected = function (artifact) {
                return $scope.selection.list[artifact.id];
            };


            // add artifact to selection
            $scope.addArtifactToSelection = function (artifact) {
                $scope.selection.artifacts.push(artifact);
                $scope.updateAllSelected();
            };

            $scope.dropArtifactFromSelection = function (artifact) {
                angular.forEach($scope.selection.artifacts, function (element) {
                    if (element.id === artifact.id) {
                        $scope.selection.artifacts.splice($scope.selection.artifacts.indexOf(element), 1);
                    }
                });
                $scope.updateAllSelected();
            };

            // select or unselect an artifact
            $scope.selectArtifact = function (artifact) {
                if ($scope.selection.list[artifact.id]) { // if artifact is  selected
                    $scope.addArtifactToSelection(artifact);
                    $scope.incDataType(artifact.dataType);
                } else { // if artifact is not selected
                    $scope.dropArtifactFromSelection(artifact);
                    $scope.decDataType(artifact.dataType);
                }
                $scope.activeAnalyzers();
            };


            // control if all artifacts are selected
            $scope.controlAllSelected = function () {
                var allSelected = true;
                if ($scope.artifacts.allValues.length === $scope.selection.artifacts.length) {

                    angular.forEach($scope.selection.list, function (value) {
                        if (!(value)) {
                            allSelected = false;
                        }
                    });
                } else {
                    allSelected = false;
                }

                return allSelected;
            };

            // update scope.selection.all
            $scope.updateAllSelected = function () {
                if ($scope.controlAllSelected()) {
                    $scope.selection.all = true;
                } else {
                    $scope.selection.all = false;
                }
            };

            // actions on artifacts

            $scope.addArtifact = function () {
                $uibModal.open({
                    animation: 'true',
                    templateUrl: 'views/partials/observables/observable.creation.html',
                    controller: 'ObservableCreationCtrl',
                    size: 'lg'
                });

            };

            $scope.dropArtifact = function (observable) {
                CaseArtifactSrv.api().delete({
                    artifactId: observable.id
                }, function () {
                    $scope.$emit('observables:observable-removed', observable);
                });
            };

            $scope.toggleTEList = function () {
                if ($scope.switchTEList) {
                    $scope.switchTEList = false;
                    $scope.initSelection($scope.selection);
                } else {
                    $scope.switchTEList = true;
                }
            };

            /**
             * Returns true if all the observables have the same set of tags.
             * Returns false otherwise of if the list of artifacts is empty
             *
             * @return {Boolean}
             */
            $scope.checkTags = function () {
                if ($scope.selection.artifacts.length < 1) {
                    return false;
                }
                var l = $scope.selection.artifacts[0].tags || [];
                return $scope.selection.artifacts.every(
                    function (te) {
                        return angular.equals((te.tags || []).sort(), l.sort());
                    });
            };

            $scope.evalTtags = function () {
                if ($scope.checkTags()) {
                    $scope.selection.ttags = $scope.objectifyTags($scope.selection.artifacts[0].tags);
                } else {
                    $scope.selection.ttags = [];
                }
            };

            $scope.stringifyTags = function (input) {
                return _.uniq(_.pluck(input, 'text'));
            };

            /**
             * Return an array of tag objects starting from array of strings
             *
             * @param  {Array} tags array of tags value
             * @return {Array} Array of tag objects ({text: value})
             */
            $scope.objectifyTags = function (tags) {
                if (!tags) {
                    return [];
                }

                return tags.sort().map(function (tag) {
                    return {
                        text: tag
                    };
                });
            };

            $scope.updateTETags = function (observable, input, haveSameTags) {
                var tags = observable.tags || [];

                if (haveSameTags) {
                    tags = (input.length === 0) ? [] : input;
                } else {
                    tags = _.uniq(tags.concat(input));
                }

                $scope.updateField(observable.id, 'tags', tags);
            };

            $scope.updateTags = function () {
                var haveSameTags = $scope.checkTags();
                var input = $scope.stringifyTags($scope.selection.ttags);

                angular.forEach($scope.selection.artifacts, function (observable) {
                    $scope.updateTETags(observable, input, haveSameTags);
                });

                $scope.selection.ttags = [];
                $scope.selection.Action = 'main';
            };

            $scope.chTLP = '-1';
            $scope.updateTLP = function (value) {
                $scope.chTLP = value;
                CaseArtifactSrv.bulkUpdate(_.pluck($scope.selection.artifacts, 'id'), {'tlp': $scope.chTLP})
                    .then(function(){
                        $scope.chTLP = '-1';
                        NotificationSrv.log('Selected observables have been updated successfully', 'success');
                        $scope.selection.Action='main';
                    });
            };

            $scope.setIOC = function (ioc) {
                CaseArtifactSrv.bulkUpdate(_.pluck($scope.selection.artifacts, 'id'), {ioc: ioc})
                    .then(function(){
                        NotificationSrv.log('Selected observables have been updated successfully', 'success');
                        $scope.selection.Action='main';
                    });
            };
            $scope.setSightedFlag = function (sighted) {
                CaseArtifactSrv.bulkUpdate(_.pluck($scope.selection.artifacts, 'id'), {sighted: sighted})
                    .then(function(){
                        NotificationSrv.log('Selected observables have been updated successfully', 'success');
                        $scope.selection.Action='main';
                    });
            };

            $scope.updateField = function (id, fieldName, newValue) {
                var field = {};
                field[fieldName] = newValue;
                return CaseArtifactSrv.api().update({
                        artifactId: id
                    }, field,
                    function () {
                        $scope.initSelection($scope.selection);
                    },
                    function (response) {
                        NotificationSrv.error('selection', response.data, response.status);
                        $scope.initSelection($scope.selection);
                    });
            };

            $scope.deleteSelectedTE = function () {
                angular.forEach($scope.selection.artifacts, function (te) {
                    $scope.dropArtifact({
                        'id': te.id
                    });
                });
                $scope.initSelection($scope.selection);
            };

            $scope.checkDataTypeList = function (analyzer, datatype) {
                var dt = analyzer.dataTypeList.toString().split(',');
                angular.forEach(dt, function (element) {
                    element = element.split(' ').join('');
                });
                return (dt.indexOf(datatype) !== -1);
            };




            //
            // Analyzers
            //

            // Update analyzersList.active list

            // increment datatypes from selection of artifacts
            // used to know is an analyzer can be selected by user or not
            $scope.incDataType = function (datatype) {
                if ($scope.analyzersList.datatypes[datatype]) {
                    $scope.analyzersList.datatypes[datatype]++;
                } else {
                    $scope.analyzersList.datatypes[datatype] = 1;
                }
            };

            $scope.decDataType = function (datatype) {
                $scope.analyzersList.datatypes[datatype]--;
            };


            $scope.activeAnalyzers = function () {
                angular.forEach($scope.analyzersList.analyzers, function (analyzer) {
                    $scope.analyzersList.active[analyzer.name] = false;
                });

                $scope.analyzersList.countDataTypes = 0;
                $scope.analyzersList.countActiveAnalyzers = {
                    total: 0
                };
                $scope.analyzersList.activeDataTypes = [];

                angular.forEach($scope.analyzersList.datatypes, function (value, key) {
                    if (value > 0) {
                        // verifier les analyzer sur cette key et mettre a true
                        $scope.analyzersList.countDataTypes++;
                        $scope.analyzersList.countActiveAnalyzers[key] = 0;

                        angular.forEach($scope.analyzersList.analyzers, function (analyzer) {
                            if ($scope.checkDataTypeList(analyzer, key)) {
                                $scope.analyzersList.active[analyzer.name] = true;
                                $scope.analyzersList.countActiveAnalyzers.total++;
                                $scope.analyzersList.countActiveAnalyzers[key]++;

                                if ($scope.analyzersList.activeDataTypes.indexOf(key) === -1) {
                                    $scope.analyzersList.activeDataTypes.push(key);
                                }
                            }
                        });
                    }
                });
            };

            $scope.selectAllAnalyzers = function(selected) {
                $scope.analyzersList.selected = _.mapObject($scope.analyzersList.selected, function(/*val, key*/) {
                    return selected;
                });
            };


            // run an Analyzer on an artifact
            $scope.runAnalyzer = function (analyzerId, artifact) {
                var artifactName = artifact.data || artifact.attachment.name;

                return CortexSrv.getServers([analyzerId])
                    .then(function (serverId) {
                        return CortexSrv.createJob({
                            cortexId: serverId,
                            artifactId: artifact.id,
                            analyzerId: analyzerId
                        });
                    })
                    .then(function () {}, function (response) {
                        if (response && response.status) {
                            NotificationSrv.log('Unable to run analyzer ' + analyzerId + ' for observable: ' + artifactName, 'error');
                        }
                    });
            };

            // run selected analyzers on selected artifacts
            $scope.runAnalyzerOnSelection = function () {
                var toRun = [];
                var nbArtifacts = $scope.selection.artifacts.length;

                angular.forEach($scope.selection.artifacts, function (element) {
                    angular.forEach($scope.analyzersList.analyzers, function (analyzer) {
                        if (($scope.analyzersList.selected[analyzer.name]) && ($scope.checkDataTypeList(analyzer, element.dataType))) {
                            toRun.push({
                                analyzerId: analyzer.name,
                                artifact: element
                            });
                        }
                    });
                });

                var analyzerIds = _.uniq(_.pluck(toRun, 'analyzerId'));

                CortexSrv.getServers(analyzerIds)
                    .then(function(serverId) {
                        return $q.all(
                            _.map(toRun, function(item) {
                                return CortexSrv.createJob({
                                    cortexId: serverId,
                                    artifactId: item.artifact.id,
                                    analyzerId: item.analyzerId
                                });
                            })
                        );
                    })
                    .then(function() {
                        NotificationSrv.log('Analyzers have been successfully started for ' + nbArtifacts + ' observables', 'success');
                    }, function() {

                    });

                $scope.initAnalyzersList();
                $scope.initSelection($scope.selection);
            };

            $scope.runAllOnObservable = function(artifact) {
                var artifactId = artifact.id;
                var artifactName = artifact.data || artifact.attachment.name;

                var analyzerIds = [];
                AnalyzerSrv.forDataType(artifact.dataType)
                    .then(function(analyzers) {
                        analyzerIds = _.pluck(analyzers, 'name');
                        return CortexSrv.getServers(analyzerIds);
                    })
                    .then(function (serverId) {
                        return $q.all(_.map(analyzerIds, function (analyzerId) {
                            return CortexSrv.createJob({
                                cortexId: serverId,
                                artifactId: artifactId,
                                analyzerId: analyzerId
                            });
                        }));
                    })
                    .then(function () {
                        NotificationSrv.log('Analyzers has been successfully started for observable: ' + artifactName, 'success');
                    });
            };

            //
            // Open an artifact
            //
            $scope.openArtifact = function (artifact) {
                $state.go('app.case.observables-item', {
                    itemId: artifact.id
                });
            };

            $scope.showReport = function(observable, analyzerId) {
                CortexSrv.getJobs($scope.caseId, observable.id, analyzerId, 1)
                    .then(function(response) {
                        return CortexSrv.getJob(response.data[0].id)
                    })
                    .then(function(response){
                        var job = response.data;
                        var report = {
                            job: job,
                            template: job.analyzerId,
                            content: job.report,
                            status: job.status,
                            startDate: job.startDate,
                            endDate: job.endDate
                        };

                        var modalInstance = $uibModal.open({
                            templateUrl: 'views/partials/observables/list/job-report-dialog.html',
                            controller: 'JobReportModalCtrl',
                            controllerAs: '$vm',
                            size: 'max',
                            resolve: {
                                report: function() {
                                    return report
                                },
                                observable: function() {
                                    return observable;
                                }
                            }
                        });
                    })
                    .catch(function(err) {
                        NotificationSrv.error('Unable to fetch the analysis report');
                    })
            }
        }
    )
    .controller('JobReportModalCtrl', function($uibModalInstance, report, observable) {
        this.report = report;
        this.observable = observable;
        this.close = function() {
            $uibModalInstance.dismiss();
        }
    });

})();
