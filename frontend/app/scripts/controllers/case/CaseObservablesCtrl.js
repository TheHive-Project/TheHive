(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseObservablesCtrl',
        function ($scope, $q, $state, $stateParams, $filter, $uibModal, ModalUtilsSrv, FilteringSrv, StreamSrv, CaseTabsSrv, PaginatedQuerySrv, CaseArtifactSrv, NotificationSrv, AnalyzerSrv, CortexSrv, VersionSrv) {

            CaseTabsSrv.activateTab($state.current.data.tab);

            $scope.analysisEnabled = VersionSrv.hasCortex();
            $scope.caseId = $stateParams.caseId;
            $scope.showText = false;
            $scope.obsResponders = null;

            $scope.selection = {
                artifacts: []
            };
            // $scope.actions = {
            //     main: 'Action',
            //     export: 'Export',
            //     changeSightedFlog: 'Change sighted flag',
            //     changeIOCFlog: 'Change IOC flag',
            //     changeTlp: 'Change TLP',
            //     addTags: 'Add tags',
            //     runAnalyzers: 'Run analyzers',
            //     remove: 'Delete'
            // };

            $scope.menu = {
                selectAll: false
            };

            this.$onInit = function() {
                $scope.filtering = new FilteringSrv('case_artifact', 'observable.list', {
                    version: 'v1',
                    defaults: {
                        showFilters: true,
                        showStats: false,
                        pageSize: 15,
                        sort: ['-startDate']
                    },
                    defaultFilter: []
                });

                $scope.filtering.initContext($scope.caseId)
                    .then(function() {
                        $scope.load();

                        $scope.initSelection($scope.selection);
                        $scope.initAnalyzersList();

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
                            $scope.filtering.setPageSize(newValue);
                        });
                    });
            };

            $scope.load = function() {
                $scope.artifacts = new PaginatedQuerySrv({
                    root: $scope.caseId,
                    objectType: 'case_artifact',
                    version: 'v1',
                    scope: $scope,
                    sort: $scope.filtering.context.sort,
                    pageSize: $scope.filtering.context.pageSize,
                    filter: $scope.filtering.buildQuery(),
                    extraData: ['seen'],
                    operations: [
                        {'_name': 'getCase', "idOrName": $scope.caseId},
                        {'_name': 'observables'}
                    ],
                    onUpdate: function() {
                        $scope.resetSelection();
                    }
                });
            };

            $scope.resetSelection = function() {
                if ($scope.menu.selectAll) {
                    $scope.selectAll();
                } else {
                    $scope.selection.artifacts = [];
                    $scope.menu.selectAll = false;
                    $scope.updateMenu();
                }
            };

            $scope.sortByField = function(field) {
                var context = this.filtering.context;
                var currentSort = Array.isArray(context.sort) ? context.sort[0] : context.sort;
                var sort = null;

                if(currentSort.substr(1) !== field) {
                    sort = ['+' + field];
                } else {
                    sort = [(currentSort === '+' + field) ? '-'+field : '+'+field];
                }

                $scope.artifacts.sort = sort;
                $scope.artifacts.update();
                $scope.filtering.setSort(sort);
            };

            $scope.keys = function(obj) {
                return _.keys(obj || {});
            };

            // ***************************************************
            $scope.toggleStats = function () {
                $scope.filtering.toggleStats();
            };

            $scope.toggleFilters = function () {
                $scope.filtering.toggleFilters();
            };

            $scope.filter = function () {
                $scope.filtering.filter().then($scope.applyFilters);
            };

            $scope.clearFilters = function () {
                $scope.filtering.clearFilters()
                    .then($scope.search);
            };

            $scope.removeFilter = function (index) {
                $scope.filtering.removeFilter(index)
                    .then($scope.search);
            };

            $scope.search = function () {
                $scope.load();
                $scope.filtering.storeContext();
            };
            $scope.addFilterValue = function (field, value) {
                $scope.filtering.addFilterValue(field, value);
                $scope.search();
            };

            $scope.filterByTlp = function(value) {
                $scope.addFilterValue('tlp', value);
            };
            // ***************************************************


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
                //selection.artifacts = [];
                selection.Action = 'main';
                // angular.forEach($scope.artifacts.allValues, function (artifact) {
                //     selection.list[artifact._id] = false;
                // });
            };

            $scope.initAnalyzersList = function () {
                if($scope.analysisEnabled) {
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
                }
            };

            //
            // update selection of artifacts each time artifacts is updated
            // $scope.updateSelection = function () {
            //     if ($scope.selection.all) {
            //         $scope.selection.list = {};
            //
            //         $scope.selection.artifacts = $scope.artifacts.allValues;
            //         angular.forEach($scope.artifacts.allValues, function (element) {
            //             $scope.selection.list[element._id] = true;
            //         });
            //     } else {
            //         var lid = _.pluck($scope.artifacts.allValues, '_id');
            //
            //         $scope.selection.artifacts.length = 0;
            //         angular.forEach($scope.selection.list, function (value, key) {
            //             var index = lid.indexOf(key);
            //             if (index >= 0) {
            //                 if (value) {
            //                     $scope.selection.artifacts.push($scope.artifacts.allValues[index]);
            //                 }
            //             } else {
            //                 delete $scope.selection.list[key];
            //             }
            //         });
            //
            //     }
            // };

            // Update the menu items
            $scope.updateMenu = function() {

            };

            // select all artifacts : add all artifacts in selection or delete selection
            $scope.selectAll = function () {
                // if ($scope.selection.all) {
                //     $scope.selection.artifacts = $scope.artifacts.allValues.slice();
                //     angular.forEach($scope.artifacts.allValues, function (element) {
                //         $scope.selection.list[element._id] = true;
                //         $scope.incDataType(element.dataType);
                //     });
                // } else {
                //     $scope.initSelection($scope.selection);
                //     $scope.initAnalyzersList();
                // }
                // $scope.activeAnalyzers();
                //

                var selected = $scope.menu.selectAll;
                _.each($scope.artifacts.values, function(item) {
                    item.selected = selected;
                });

                if (selected) {
                    $scope.selection.artifacts = $scope.artifacts.values;
                } else {
                    $scope.selection.artifacts = [];

                    // $scope.initSelection($scope.selection);
                    $scope.initAnalyzersList();
                }

                $scope.activeAnalyzers();

                $scope.updateMenu();
            };

            // select or unselect an artifact
            $scope.selectArtifact = function (artifact) {
                // if ($scope.selection.list[artifact._id]) { // if artifact is  selected
                //     $scope.addArtifactToSelection(artifact);
                //     $scope.incDataType(artifact.dataType);
                // } else { // if artifact is not selected
                //     $scope.dropArtifactFromSelection(artifact);
                //     $scope.decDataType(artifact.dataType);
                // }
                // $scope.activeAnalyzers();
                //
                if (artifact.selected) {
                    $scope.selection.artifacts.push(artifact);
                } else {
                    $scope.selection.artifacts = _.reject($scope.selection.artifacts, function(item) {
                        return item._id === artifact._id;
                    });
                }

                $scope.updateMenu();
            };

            // // add artifact to selection
            // $scope.addArtifactToSelection = function (artifact) {
            //     $scope.selection.artifacts.push(artifact);
            //     $scope.updateAllSelected();
            // };

            // $scope.dropArtifactFromSelection = function (artifact) {
            //     angular.forEach($scope.selection.artifacts, function (element) {
            //         if (element._id === artifact._id) {
            //             $scope.selection.artifacts.splice($scope.selection.artifacts.indexOf(element), 1);
            //         }
            //     });
            //     $scope.updateAllSelected();
            // };

            // control if all artifacts are selected
            // $scope.controlAllSelected = function () {
            //     var allSelected = true;
            //     if ($scope.artifacts.allValues.length === $scope.selection.artifacts.length) {
            //
            //         angular.forEach($scope.selection.list, function (value) {
            //             if (!(value)) {
            //                 allSelected = false;
            //             }
            //         });
            //     } else {
            //         allSelected = false;
            //     }
            //
            //     return allSelected;
            // };

            // update scope.selection.all
            // $scope.updateAllSelected = function () {
            //     if ($scope.controlAllSelected()) {
            //         $scope.selection.all = true;
            //     } else {
            //         $scope.selection.all = false;
            //     }
            // };

            // actions on artifacts
            $scope.addArtifact = function () {
                $uibModal.open({
                    animation: 'true',
                    templateUrl: 'views/partials/observables/observable.creation.html',
                    controller: 'ObservableCreationCtrl',
                    size: 'lg',
                    resolve: {
                        params: function() {
                            return null;
                        },
                        tags: function() {
                            return [];
                        }
                    }
                });
            };

            $scope.bulkEdit = function() {
                var modal = $uibModal.open({
                    animation: 'true',
                    templateUrl: 'views/partials/observables/observable.update.html',
                    controller: 'ObservableUpdateCtrl',
                    controllerAs: '$dialog',
                    size: 'lg',
                    resolve: {
                        selection: function() {
                            return $scope.selection.artifacts;
                        }
                    }
                });

                modal.result.then(function(operations) {
                    $q.all(_.map(operations, function(operation) {
                        return CaseArtifactSrv.bulkUpdate(operation.ids, operation.patch);
                    })).then(function(/*responses*/) {
                        NotificationSrv.log('Selected observables have been updated successfully', 'success');
                    });
                });
            };

            $scope.showExport = function() {
                $scope.showExportPanel = true;
            };

            $scope.hideExport = function() {
                $scope.showExportPanel = false;
                $scope.initSelection($scope.selection);
            };

            $scope.removeObservables = function () {

                ModalUtilsSrv.confirm('Remove Observables', 'Are you sure you want to delete the selected Observables?', {
                    okText: 'Yes, remove them',
                    flavor: 'danger'
                }).then(function() {

                    $q.all(_.map($scope.selection.artifacts, function(observable) {
                        return CaseArtifactSrv.api().delete({
                            artifactId: observable._id
                        }, function () {
                            $scope.$emit('observables:observable-removed', observable);
                        }).$promise;
                    }));
                }).then(function(/*responses*/) {
                    NotificationSrv.log('The selected observables have been deleted', 'success');
                }).catch(function(/*err*/) {
                    //NotificationSrv.error('Observable deletion', response.data, response.status);
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
                            artifactId: artifact._id,
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
                                    artifactId: item.artifact._id,
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
                var artifactId = artifact._id;
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
                    itemId: artifact._id
                });
            };

            $scope.showReport = function(observable, analyzerId) {
                CortexSrv.getJobs($scope.caseId, observable._id, analyzerId, 1)
                    .then(function(response) {
                        return CortexSrv.getJob(response.data[0]._id);
                    })
                    .then(function(response){
                        var job = response.data;
                        var report = {
                            job: job,
                            template: job.analyzerName || job.analyzerId,
                            content: job.report,
                            status: job.status,
                            startDate: job.startDate,
                            endDate: job.endDate
                        };

                        $uibModal.open({
                            templateUrl: 'views/partials/observables/list/job-report-dialog.html',
                            controller: 'JobReportModalCtrl',
                            controllerAs: '$vm',
                            size: 'max',
                            resolve: {
                                report: function() {
                                    return report;
                                },
                                observable: function() {
                                    return observable;
                                }
                            }
                        });
                    })
                    .catch(function(/*err*/) {
                        NotificationSrv.error('Unable to fetch the analysis report');
                    });
            };

            $scope.getObsResponders = function(observableId, force) {
                if(!force && $scope.obsResponders !== null) {
                   return;
                }

                $scope.obsResponders = null;
                CortexSrv.getResponders('case_artifact', observableId)
                  .then(function(responders) {
                      $scope.obsResponders = responders;
                  })
                  .catch(function(err) {
                      NotificationSrv.error('observablesList', err.data, err.status);
                  });
            };

            $scope.runResponder = function(responderId, responderName, artifact) {
                CortexSrv.runResponder(responderId, responderName, 'case_artifact', _.pick(artifact, 'id'))
                  .then(function(response) {
                      var data = '['+$filter('fang')(artifact.data || artifact.attachment.name)+']';
                      NotificationSrv.log(['Responder', response.data.responderName, 'started successfully on observable', data].join(' '), 'success');
                  })
                  .catch(function(response) {
                      if(response && !_.isString(response)) {
                          NotificationSrv.error('observablesList', response.data, response.status);
                      }
                  });
            };
        }
    )
    .controller('JobReportModalCtrl', function($uibModalInstance, report, observable) {
        this.report = report;
        this.observable = observable;
        this.close = function() {
            $uibModalInstance.dismiss();
        };
    });

})();
