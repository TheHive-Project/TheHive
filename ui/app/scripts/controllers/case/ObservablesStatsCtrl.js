/**
 * Controller for About The Hive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('ObservablesStatsCtrl',
        function($rootScope, $stateParams, $timeout, StatSrv, StreamStatSrv, ObservablesUISrv) {
            var self = this;

            this.uiSrv = ObservablesUISrv;

            this.byType = {};
            this.byIoc = {};
            this.byTags = {};

            var defaultQuery = {
                '_and': [{
                    '_parent': {
                        '_type': 'case',
                        '_query': {
                            '_id': $stateParams.caseId
                        }
                    }
                }, {
                    'status': 'Ok'
                }]
            };

            // Get stats by tags
            StreamStatSrv({
                rootId: $stateParams.caseId,
                query: defaultQuery,
                objectType: 'case_artifact',
                field: 'tags',
                sort: ['_count'],
                limit: 10,
                result: {},
                success: function(data){
                    self.byTags = self.prepareResult(data);
                }
            });

            // Get stats by type
            StreamStatSrv({
                rootId: $stateParams.caseId,
                query: defaultQuery,
                objectType: 'case_artifact',
                field: 'dataType',
                result: {},
                success: function(data){
                    self.byType = self.prepareResult(data);
                }
            });

            // Get stats by ioc
            StreamStatSrv({
                rootId: $stateParams.caseId,
                query: defaultQuery,
                objectType: 'case_artifact',
                field: 'ioc',
                result: {},
                success: function(data){
                    self.byIoc = self.prepareResult(data);
                }
            });

            this.prepareResult = function(rawStats) {
                var total = rawStats.count;

                var keys = _.without(_.keys(rawStats), 'count');
                var columns = keys.map(function(key) {
                    return {
                        key: key,
                        count: rawStats[key].count
                    };
                }).sort(function(a, b) {
                    return a.count <= b.count;
                });

                return {
                    total: total,
                    details: columns
                };
            };

            this.filterBy = function(field, value) {
                this.uiSrv.addFilter(field, value);
            };

            /*
            $scope.observableByType = {
                title: 'Observables by Type',
                type: 'case_artifact',
                field: 'dataType',
                dateField: 'startDate',
                tagsField: 'tags'
            };

            $scope.observableByTags = {
                title: 'Top 10 tags',
                type: 'case_artifact',
                field: 'tags',
                dateField: 'startDate',
                tagsField: 'tags',
                limit: 10,
                sort: ['-count']
            };

            $scope.observableByIoc = {
                title: 'IOCs',
                type: 'case_artifact',
                field: 'ioc',
                dateField: 'startDate',
                tagsField: 'tags',
                colors: {
                    '0': '#5cb85c',
                    '1': '#d9534f'
                },
                names: {
                    '0': 'Not IOC',
                    '1': 'IOC'
                }
            };

            // Prepare the global query
            $scope.prepareGlobalQuery = function() {
                return function(options) {
                    return {
                        _and: [{
                            _parent: $stateParams.caseId
                        }]
                    };
                };
            };

            $scope.filter = function() {
                $rootScope.$broadcast('refresh-charts', $scope.prepareGlobalQuery());
            };

            $scope.filter();

            $timeout(function() {
                $scope.filter();
            }, 1000);
            */
        }
    );
})();
