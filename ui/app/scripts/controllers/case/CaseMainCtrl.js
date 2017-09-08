(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseMainCtrl',
        function($scope, $rootScope, $state, $stateParams, $q, $uibModal, CaseTabsSrv, CaseSrv, MetricsCacheSrv, UserInfoSrv, MispSrv, StreamStatSrv, NotificationSrv, UtilsSrv, CaseResolutionStatus, CaseImpactStatus, caze) {
            $scope.CaseResolutionStatus = CaseResolutionStatus;
            $scope.CaseImpactStatus = CaseImpactStatus;

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
            $scope.getUserInfo = UserInfoSrv;
            $scope.caseId = caseId;
            $scope.links = [];
            $scope.newestLink = null;
            $scope.oldestLink = null;

            $scope.caze = caze;
            $rootScope.title = 'Case #' + caze.caseId + ': ' + caze.title;

            $scope.updateMetricsList = function() {
                MetricsCacheSrv.all().then(function(metrics) {
                    $scope.allMetrics = _.omit(metrics, _.keys($scope.caze.metrics));
                    $scope.metricsAvailable = _.keys($scope.allMetrics).length > 0;
                });
            };

            $scope.countIoc = function(link) {
                return _.filter(link.linkedWith, function(l) {
                    return l.ioc;
                }).length;
            };

            $scope.updateMetricsList();

            CaseSrv.links({
                caseId: $scope.caseId
            }, function(data) {
                $scope.links = data;

                if (data.length > 0) {
                    $scope.newestLink = data[0];
                    $scope.newestLink.iocCount = $scope.countIoc($scope.newestLink);
                }

                if (data.length > 1) {
                    $scope.oldestLink = data[data.length - 1];
                    $scope.oldestLink.iocCount = $scope.countIoc($scope.oldestLink);
                }
            });

            $scope.tasks = StreamStatSrv({
                scope: $scope,
                rootId: caseId,
                query: {
                    '_and': [{
                        '_parent': {
                            "_type": "case",
                            "_query": {
                                "_id": caseId
                            }
                        }
                    }, {
                        '_not': {
                            'status': 'Cancel'
                        }
                    }]
                },
                result: {},
                objectType: 'case_task',
                field: 'status'
            });

            $scope.artifactStats = StreamStatSrv({
                scope: $scope,
                rootId: caseId,
                query: {
                    '_and': [{
                        '_parent': {
                            "_type": "case",
                            "_query": {
                                "_id": caseId
                            }
                        }
                    }, {
                        'status': 'Ok'
                    }]
                },
                result: {},
                objectType: 'case_artifact',
                field: 'status'
            });

            $scope.$on('tasks:task-removed', function(event, task) {
                CaseTabsSrv.removeTab('task-' + task.id);
            });
            $scope.$on('observables:observable-removed', function(event, observable) {
                CaseTabsSrv.removeTab('observable-' + observable.id);
            });

            $scope.openTab = function(tabName) {
                var tab = CaseTabsSrv.getTab(tabName),
                    params = angular.extend({}, $state.params, tab.params || {});

                $state.go(tab.state, params);
            };

            $scope.removeTab = function(tab) {
                var switchToDetails = CaseTabsSrv.removeTab(tab);

                if(switchToDetails) {
                    $scope.openTab('details');
                }
            };

            $scope.switchFlag = function() {
                if ($scope.caze.flag === true) {
                    $scope.updateField('flag', false);
                } else {
                    $scope.updateField('flag', true);
                }
            };

            // update a specific case field
            $scope.updateField = function(fieldName, newValue) {
                var data = {};

                if (angular.isString(fieldName)) {
                    data[fieldName] = newValue;
                } else {
                    data = fieldName;
                }

                var defer = $q.defer();

                CaseSrv.update({
                    caseId: caseId
                }, data, function(response) {
                    UtilsSrv.shallowClearAndCopy(response, $scope.caze);
                    defer.resolve($scope.caze);
                }, function(response) {
                    NotificationSrv.error('caseDetails', response.data, response.status);
                    defer.reject(response);
                });

                return defer.promise;
            };

            $scope.isCaseClosed = function() {
                return $scope.caze.status === 'Resolved';
            };

            $scope.isCaseTruePositive = function() {
                return $scope.caze.resolutionStatus === 'TruePositive';
            };

            $scope.openCloseDialog = function() {
                var modalInstance = $uibModal.open({
                    scope: $scope,
                    templateUrl: 'views/partials/case/case.close.html',
                    controller: 'CaseCloseModalCtrl',
                    size: 'lg'
                });

                modalInstance.result.then(function() {
                    $state.go('app.cases');
                });
            };

            $scope.reopenCase = function() {
                $uibModal.open({
                    scope: $scope,
                    templateUrl: 'views/partials/case/case.reopen.html',
                    controller: 'CaseReopenModalCtrl',
                    size: ''
                });
            };

            $scope.mergeCase = function() {
                $uibModal.open({
                    templateUrl: 'views/partials/case/case.merge.html',
                    controller: 'CaseMergeModalCtrl',
                    controllerAs: 'dialog',
                    size: 'lg',
                    resolve: {
                        caze: function() {
                            return $scope.caze;
                        }
                    }
                });
            };


            var extractExportErrors = function (errors) {
                var result = [];

                console.log(errors);
                result = errors.map(function(item) {
                    return {
                        data: item.object.dataType === 'file' ? item.object.attachment.name : item.object.data,
                        message: item.message
                    };
                });

                return result;
            }

            var showExportErrors = function(errors) {
                $uibModal.open({
                    templateUrl: 'views/partials/misp/error.dialog.html',
                    controller: function(clipboard, $uibModalInstance, failures) {
                        this.failures = failures;
                        this.cancel = function() {
                            $uibModalInstance.dismiss();
                        }

                        this.copyToClipboard = function() {
                            clipboard.copyText(_.pluck(failures, 'data').join('\n'));
                            $uibModalInstance.dismiss();
                        }
                    },
                    controllerAs: 'dialog',
                    size: 'lg',
                    resolve: {
                        failures: function() {
                            return errors;
                        }
                    }
                })
            }

            $scope.shareCase = function() {
                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/misp/case.export.confirm.html',
                    controller: function($uibModalInstance, data) {
                        this.caze = data;
                        this.cancel = function() {
                            $uibModalInstance.dismiss();
                        }

                        this.confirm = function() {
                            $uibModalInstance.close();
                        }
                    },
                    controllerAs: 'dialog',
                    resolve: {
                        data: function() {
                            return $scope.caze;
                        }
                    }
                });

                modalInstance.result.then(function() {
                    var mispConfig = $scope.appConfig.connectors.misp;
                    return MispSrv.getServer(mispConfig)
                }).then(function(server) {
                    return MispSrv.export($scope.caseId, server);
                })
                .then(function(response){
                    var success = 0,
                        failure = 0;

                    if (response.status === 207) {
                        success = response.data.success.length;
                        failure = response.data.failure.length;

                        showExportErrors(extractExportErrors(response.data.failure));

                        NotificationSrv.log('The case has been successfully exported, but '+ failure +' observable(s) failed', 'warning');
                    } else {
                        success = angular.isObject(response.data) ? 1 : response.data.length;
                        NotificationSrv.log('The case has been successfully exported with ' + success+ ' observable(s)', 'success');
                    }

                }, function(err) {
                    if(!err) {
                        return;
                    }

                    if (err.status === 400) {
                        showExportErrors(extractExportErrors(err.data));
                    } else {
                        NotificationSrv.error('CaseExportCtrl', 'An unexpected error occurred while exporting case', response.status);
                    }
                });

            };

            /**
             * A workaround filter to make sure the ngRepeat doesn't order the
             * object keys
             */
            $scope.notSorted = function(obj) {
                if (!obj) {
                    return [];
                }
                return Object.keys(obj);
            };

            $scope.getTags = function(selection) {
                var tags = [];

                angular.forEach(selection, function(tag) {
                    tags.push(tag.text);
                });

                return tags;
            };
        }
    );
})();
