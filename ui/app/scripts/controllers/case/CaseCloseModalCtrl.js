(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseCloseModalCtrl',
        function($scope, $uibModalInstance, SearchSrv, MetricsCacheSrv, NotificationSrv) {

            $scope.tasksValid = false;
            $scope.tasks = [];
            $scope.formData = {};

            SearchSrv(function(data) {
                $scope.initialize();
                $scope.tasks = data;

                if (data && data.length === 0) {
                    $scope.tasksValid = true;
                }
            }, {
                '_and': [{
                    '_parent': {
                        "_query": {
                            "_id": $scope.caze.id
                        },
                        "_type": "case"
                    }
                }, {
                    '_in': {
                        '_field': 'status',
                        '_values': ['Waiting', 'InProgress']
                    }
                }]
            }, 'case_task', 'all');


            $scope.initialize = function() {
                MetricsCacheSrv.all().then(function(metricsCache) {

                    $scope.formData = {
                        status: 'Resolved',
                        resolutionStatus: $scope.caze.resolutionStatus || 'Indeterminate',
                        summary: $scope.caze.summary || '',
                        impactStatus: $scope.caze.impactStatus || null
                    };

                    $scope.metricsCache = metricsCache;

                    $scope.$watchCollection('formData', function(data, oldData) {
                        if (data.resolutionStatus !== oldData.resolutionStatus) {
                            data.impactStatus = null;
                        }
                    });
                });
            };

            $scope.confirmTaskClose = function() {
                $scope.tasksValid = true;
            };

            $scope.closeCase = function() {
                var data = $scope.formData;

                if (data.impactStatus === null) {
                    data.impactStatus = 'NotApplicable';
                }

                var promise = $scope.updateField(_.extend({
                    metrics: $scope.caze.metrics
                }, data));

                promise.then(function(caze) {
                    $scope.caze = caze;

                    NotificationSrv.log('The case #' + caze.caseId + ' has been closed', 'success');

                    $uibModalInstance.close();
                });
            };

            $scope.cancel = function() {
                $uibModalInstance.dismiss();
            };
        }
    );
})();
