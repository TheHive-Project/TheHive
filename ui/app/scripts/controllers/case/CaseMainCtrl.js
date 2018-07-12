(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseMainCtrl',
        function($scope, $rootScope, $state, $stateParams, $q, $uibModal, CaseTabsSrv, CaseSrv, MetricsCacheSrv, UserInfoSrv, MispSrv, StreamSrv, StreamStatSrv, NotificationSrv, UtilsSrv, CaseResolutionStatus, CaseImpactStatus, caze) {
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

            $scope.initExports = function() {
                $scope.existingExports = _.filter($scope.caze.stats.alerts || [], function(item) {
                    return item.type === 'misp';
                }).length;
            };
            $scope.initExports();

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
                $scope.links = _.map(data, function(item){
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
                callback: function(updates) {
                  CaseSrv.get({
                      'caseId': $stateParams.caseId,
                      'nstats': true
                  }, function(data) {
                      $scope.caze = data;

                      if(updates.length === 1 && updates[0] && updates[0].base.details.customFields){
                          $scope.$broadcast('case:refresh-custom-fields');
                      }

                  }, function(response) {
                      NotificationSrv.error('CaseMainCtrl', response.data, response.status);
                  });
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

            $scope.shareCase = function() {
                if($scope.appConfig.connectors.misp && $scope.appConfig.connectors.misp.servers.length === 0) {
                    NotificationSrv.log('There are no MISP servers defined', 'error');
                    return;
                }

                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/misp/case.export.confirm.html',
                    controller: 'CaseExportDialogCtrl',
                    controllerAs: 'dialog',
                    size: 'lg',
                    resolve: {
                        caze: function() {
                            return $scope.caze;
                        },
                        config: function() {
                            return $scope.appConfig.connectors.misp;
                        }
                    }
                });

                modalInstance.result.then(function() {
                    return CaseSrv.get({
                        'caseId': $scope.caseId,
                        'nstats': true
                    }).$promise;
                }).then(function(data) {
                    $scope.caze = data.toJSON();
                    $scope.initExports();
                });
            };

            $scope.removeCase = function() {
              var modalInstance = $uibModal.open({
                  templateUrl: 'views/partials/case/case.delete.confirm.html',
                  controller: 'CaseDeleteModalCtrl',
                  resolve: {
                      caze: function() {
                          return $scope.caze;
                      }
                  }
              });

              modalInstance.result.then(function() {
                  $state.go('app.cases');
              })
              .catch(function(err) {
                  if(err && !_.isString(err)) {
                      NotificationSrv.error('caseDetails', response.data, response.status);
                  }
              });
            }

            $scope.caseReport = function() {
                $uibModal.open({
                    scope: $scope,
                    templateUrl: 'views/partials/case/case.report.html',
                    controller: 'CaseReportModalCtrl',
                    size: 'lg',
                    resolve: {
                        caze: function() {
                            return $scope.caze;
                        }
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
