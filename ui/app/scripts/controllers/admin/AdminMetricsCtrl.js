(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminMetricsCtrl',
        function($scope, ListSrv, MetricsCacheSrv, AlertSrv) {
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

                    $scope.metrics = _.values(response).filter(_.isString).map(function(item) {
                        return JSON.parse(item);
                    });

                }, function(response) {
                    AlertSrv.error('AdminMetricsCtrl', response.data, response.status);
                });
            };
            $scope.initMetrics();

            $scope.addMetric = function() {
                ListSrv.save({
                        'listId': 'case_metrics'
                    }, {
                        'value': JSON.stringify($scope.metric)
                    }, function() {
                        $scope.initMetrics();

                        MetricsCacheSrv.clearCache();
                    },
                    function(response) {
                        AlertSrv.error('AdminMetricsCtrl', response.data, response.status);
                    });
            };
        });
})();
