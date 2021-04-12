(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseMainCtrl',
        function ($scope, $rootScope, $state, $stateParams, $q, $uibModal, CaseTabsSrv, CaseSrv, UserSrv, StreamSrv, StreamQuerySrv, NotificationSrv, UtilsSrv, CaseResolutionStatus, CaseImpactStatus, CortexSrv, caze) {
            $scope.CaseResolutionStatus = CaseResolutionStatus;
            $scope.CaseImpactStatus = CaseImpactStatus;
            $scope.caseResponders = null;

            var caseId = $stateParams.caseId;
            if (!$rootScope.currentCaseId) {
                $rootScope.currentCaseId = caseId;
            }

            if (caseId !== $rootScope.currentCaseId) {
                $rootScope.currentCaseId = caseId;
                CaseTabsSrv.initTabs();
            }

            $scope.tabSrv = CaseTabsSrv;
            $scope.tabs = CaseTabsSrv.getTabs();
            $scope.getUserInfo = UserSrv.getCache;
            $scope.caseId = caseId;
            $scope.links = [];
            $scope.newestLink = null;
            $scope.oldestLink = null;

            $scope.caze = caze;
            $scope.userPermissions = (caze.extraData.permissions || []).join(',');
            $rootScope.title = 'Case #' + caze.number + ': ' + caze.title;

            $scope.canEdit = caze.extraData.permissions.indexOf('manageCase') !== -1;

            $scope.initExports = function () {
                $scope.existingExports = _.filter($scope.caze.extraData.alerts || [], function (item) {
                    return item.type === 'misp';
                }).length;
            };
            $scope.initExports();

            $scope.countIoc = function (link) {
                return _.filter(link.linkedWith, function (l) {
                    return l.ioc;
                }).length;
            };

            CaseSrv.links({
                caseId: $scope.caseId
            }, function (data) {
                $scope.links = _.map(data, function (item) {
                    item.linksCount = item.linkedWith.length || 0;

                    return item;
                });

                if (data.length > 0) {
                    $scope.newestLink = data[0];
                    $scope.newestLink.iocCount = $scope.countIoc($scope.newestLink);
                }

                if (data.length > 1) {
                    $scope.oldestLink = data[data.length - 1];
                    $scope.oldestLink.iocCount = $scope.countIoc($scope.oldestLink);
                }
            });

            StreamSrv.addListener({
                scope: $scope,
                rootId: $scope.caseId,
                objectType: 'case',
                callback: function (updates) {
                    CaseSrv.getById($stateParams.caseId, true)
                        .then(function (data) {
                            $scope.caze = data;

                            if (updates.length === 1 && updates[0] && updates[0].base.details.customFields) {
                                $scope.$broadcast('case:refresh-custom-fields');
                            }
                        }).catch(function (response) {
                            NotificationSrv.error('CaseMainCtrl', response.data, response.status);
                        });
                }
            });

            // Stats for case tasks counter
            StreamQuerySrv('v1', [
                { _name: 'countTask', caseId: caseId },
                // {_name: 'getCase', idOrName: caseId},
                // {_name: 'tasks'},
                // {_name: 'filter',
                //     _not: {
                //         '_field': 'status',
                //         '_value': 'Cancel'
                //     }
                // },
                // {_name: 'count'}
            ], {
                scope: $scope,
                rootId: caseId,
                objectType: 'case_task',
                query: {
                    params: {
                        name: 'task-stats-' + caseId
                    }
                },
                guard: UtilsSrv.hasAddDeleteEvents,
                onUpdate: function (updates) {
                    $scope.tasksCount = updates;
                }
            });

            // Stats for case observables counter
            StreamQuerySrv('v1', [
                { _name: 'countObservable', caseId: caseId },
            ], {
                scope: $scope,
                rootId: caseId,
                objectType: 'case_artifact',
                query: {
                    params: {
                        name: 'observable-stats-' + caseId
                    }
                },
                guard: UtilsSrv.hasAddDeleteEvents,
                onUpdate: function (updates) {
                    $scope.observablesCount = updates;
                }
            });

            // Stats for case alerts counter
            StreamQuerySrv('v1', [
                { _name: 'countRelatedAlert', caseId: caseId },
            ], {
                scope: $scope,
                rootId: caseId,
                objectType: 'alert',
                query: {
                    params: {
                        name: 'alert-stats-' + caseId
                    }
                },
                onUpdate: function (updates) {
                    $scope.alertCount = updates;
                }
            });

            $scope.$on('tasks:task-removed', function (event, task) {
                CaseTabsSrv.removeTab('task-' + task._id);
            });
            $scope.$on('observables:observable-removed', function (event, observable) {
                CaseTabsSrv.removeTab('observable-' + observable._id);
            });

            $scope.openTab = function (tabName) {
                var tab = CaseTabsSrv.getTab(tabName),
                    params = angular.extend({}, $state.params, tab.params || {});

                $state.go(tab.state, params);
            };

            $scope.removeTab = function (tab) {
                var switchToDetails = CaseTabsSrv.removeTab(tab);

                if (switchToDetails) {
                    $scope.openTab('details');
                }
            };

            $scope.switchFlag = function () {
                if ($scope.caze.flag === true) {
                    $scope.updateField('flag', false);
                } else {
                    $scope.updateField('flag', true);
                }
            };

            // update a specific case field
            $scope.updateField = function (fieldName, newValue) {
                var data = {};

                if (angular.isString(fieldName)) {
                    data[fieldName] = newValue;
                } else {
                    data = fieldName;
                }

                var defer = $q.defer();

                CaseSrv.update({
                    caseId: $scope.caseId
                }, data, function (/*response*/) {
                    //UtilsSrv.shallowClearAndCopy(response, $scope.caze);
                    defer.resolve($scope.caze);
                }, function (response) {
                    NotificationSrv.error('caseDetails', response.data, response.status);
                    defer.reject(response);
                });

                return defer.promise;
            };

            $scope.isCaseClosed = function () {
                return $scope.caze.status === 'Resolved';
            };

            $scope.isCaseTruePositive = function () {
                return $scope.caze.resolutionStatus === 'TruePositive';
            };

            $scope.openCloseDialog = function () {
                var modalInstance = $uibModal.open({
                    scope: $scope,
                    templateUrl: 'views/partials/case/case.close.html',
                    controller: 'CaseCloseModalCtrl',
                    size: 'lg',
                    resolve: {
                        caze: function () {
                            return angular.copy($scope.caze);
                        }
                    }
                });

                modalInstance.result.then(function () {
                    $state.go('app.cases');
                });
            };

            $scope.reopenCase = function () {
                $uibModal.open({
                    scope: $scope,
                    templateUrl: 'views/partials/case/case.reopen.html',
                    controller: 'CaseReopenModalCtrl',
                });
            };

            $scope.mergeCase = function () {
                var caseModal = $uibModal.open({
                    templateUrl: 'views/partials/case/case.merge.html',
                    controller: 'CaseMergeModalCtrl',
                    controllerAs: 'dialog',
                    size: 'lg',
                    resolve: {
                        source: function () {
                            return $scope.caze;
                        },
                        title: function () {
                            return 'Merge Case #' + $scope.caze.number;
                        },
                        prompt: function () {
                            return '#' + $scope.caze.number + ': ' + $scope.caze.title;
                        }
                    }
                });

                caseModal.result.then(function (selectedCase) {
                    CaseSrv.merge([$scope.caze._id, selectedCase._id])
                        .then(function (response) {
                            var merged = response.data;

                            $state.go('app.case.details', {
                                caseId: merged._id
                            });

                            NotificationSrv.log('The cases have been successfully merged into a new case #' + merged.number, 'success');
                        })
                        .catch(function (response) {
                            //this.pendingAsync = false;
                            NotificationSrv.error('Case Merge', response.data, response.status);
                        })

                    // CaseSrv.merge({}, {
                    //     caseId: $scope.caze.id,
                    //     mergedCaseId: selectedCase.id
                    // }, , );
                }).catch(function (err) {
                    if (err && !_.isString(err)) {
                        NotificationSrv.error('Case Merge', err.data, err.status);
                    }
                });
            };

            $scope.exportToMisp = function () {
                if ($scope.appConfig.connectors.misp && $scope.appConfig.connectors.misp.servers.length === 0) {
                    NotificationSrv.log('There are no MISP servers defined', 'error');
                    return;
                }

                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/misp/case.export.confirm.html',
                    controller: 'CaseExportDialogCtrl',
                    controllerAs: 'dialog',
                    size: 'lg',
                    resolve: {
                        caze: function () {
                            return $scope.caze;
                        },
                        config: function () {
                            return $scope.appConfig.connectors.misp;
                        }
                    }
                });

                modalInstance.result.then(function () {
                    return CaseSrv.getById($scope.caseId, true);
                    // return CaseSrv.get({
                    //     'caseId': $scope.caseId,
                    //     'nstats': true
                    // }).$promise;
                }).then(function (data) {
                    $scope.caze = data;
                    $scope.initExports();
                });
            };

            $scope.removeCase = function () {
                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/case/case.delete.confirm.html',
                    controller: 'CaseDeleteModalCtrl',
                    resolve: {
                        caze: function () {
                            return $scope.caze;
                        }
                    }
                });

                modalInstance.result.then(function () {
                    $state.go('app.cases');
                })
                    .catch(function (err) {
                        if (err && !_.isString(err)) {
                            NotificationSrv.error('caseDetails', err.data, err.status);
                        }
                    });
            };

            $scope.getCaseResponders = function (force) {
                if (!force && $scope.caseResponders !== null) {
                    return;
                }

                $scope.caseResponders = null;
                CortexSrv.getResponders('case', $scope.caseId)
                    .then(function (responders) {
                        $scope.caseResponders = responders;
                        return CortexSrv.promntForResponder(responders);
                    })
                    .then(function (response) {
                        if (response && _.isString(response)) {
                            NotificationSrv.log(response, 'warning');
                        } else {
                            return CortexSrv.runResponder(response.id, response.name, 'case', _.pick($scope.caze, '_id', 'tlp', 'pap'));
                        }
                    })
                    .then(function (response) {
                        NotificationSrv.log(['Responder', response.data.responderName, 'started successfully on case', $scope.caze.title].join(' '), 'success');
                    })
                    .catch(function (err) {
                        if (err && !_.isString(err)) {
                            NotificationSrv.error('caseDetails', err.data, err.status);
                        }
                    });
            };

            /**
             * A workaround filter to make sure the ngRepeat doesn't order the
             * object keys
             */
            $scope.notSorted = function (obj) {
                if (!obj) {
                    return [];
                }
                return Object.keys(obj);
            };

            $scope.keys = function (obj) {
                return _.keys(obj);
            };

            $scope.getTags = function (selection) {
                var tags = [];

                angular.forEach(selection, function (tag) {
                    tags.push(tag.text);
                });

                return tags;
            };
        }
    );
})();
