(function() {
    'use strict';

    angular.module('theHiveControllers').controller('CaseDetailsCtrl',
        function($scope, $state, $modal, CaseTabsSrv, UserInfoSrv, PSearchSrv) {

            CaseTabsSrv.activateTab($state.current.data.tab);

            $scope.isDefined = false;
            $scope.state = {
                'editing': false,
                'isCollapsed': true
            };

            $scope.attachments = PSearchSrv($scope.caseId, 'case_task_log', {
                'filter': {
                    '_and': [{
                        '_not': {
                            'status': 'Deleted'
                        }
                    }, {
                        '_contains': 'attachment.id'
                    }, {
                        '_parent': {
                            '_type': 'case_task',
                            '_query': {
                                '_parent': {
                                    '_type': 'case',
                                    '_query': {
                                        '_id': $scope.caseId
                                    }
                                }
                            }
                        }
                    }]
                },
                'pageSize': 100,
                'nparent': 1
            });

            $scope.hasNoMetrics = function(caze) {
                return !caze.metrics || _.keys(caze.metrics).length === 0 || caze.metrics.length === 0;
            };

            $scope.addMetric = function(metric) {
                var modalInstance = $modal.open({
                    scope: $scope,
                    templateUrl: 'views/partials/case/case.add.metric.html',
                    controller: 'CaseAddMetricConfirmCtrl',
                    size: '',
                    resolve: {
                        metric: function() {
                            return metric;
                        }
                    }
                });

                modalInstance.result.then(function() {
                    if (!$scope.caze.metrics) {
                        $scope.caze.metrics = {};
                    }
                    $scope.caze.metrics[metric.name] = null;
                    $scope.updateField('metrics', $scope.caze.metrics);
                    $scope.updateMetricsList();
                });
            };

            $scope.openAttachment = function(attachment) {
                $state.go('app.case.tasks-item', {
                    caseId: $scope.caze.id,
                    itemId: attachment.case_task.id
                });
            };
        }
    );

    angular.module('theHiveControllers').controller('CaseAddMetricConfirmCtrl', function($scope, $modalInstance, metric) {
        $scope.metric = metric;

        $scope.cancel = function() {
            $modalInstance.dismiss(metric);
        };

        $scope.confirm = function() {
            $modalInstance.close(metric);
        };
    });

})();
