(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminMetricsCtrl',
        function($scope, ListSrv, MetricsCacheSrv, NotificationSrv) {
            $scope.metrics = [];

            $scope.initMetrics = function() {
                $scope.metric = {
                    name: '',
                    title: '',
                    description: ''
                };

                ListSrv.query({
                    'listId': 'case_metrics'
                }, {}, function(response) {
                    $scope.metrics = _.map(response.toJSON(), function(value, metricId) {
                        value.id = metricId;
                        return value;
                    });

                }, function(response) {
                    NotificationSrv.error('AdminMetricsCtrl', response.data, response.status);
                });
            };
            $scope.initMetrics();

            $scope.addMetric = function() {
                ListSrv.save({
                        'listId': 'case_metrics'
                    }, {
                        'value': $scope.metric
                    }, function() {
                        $scope.initMetrics();

                        MetricsCacheSrv.clearCache();

                        $scope.$emit('metrics:refresh');
                    },
                    function(response) {
                        NotificationSrv.error('AdminMetricsCtrl', response.data, response.status);
                    });
            };
        });
})();
