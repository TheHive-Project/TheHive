/**
 * Controller for About The Hive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('MispStatsCtrl',
        function($rootScope, $scope, $stateParams, $timeout, StatSrv, StreamStatSrv, FilteringSrv) {
            var self = this;

            this.filtering = FilteringSrv;

            this.bySources = {};
            this.byStatus = {};
            this.byTags = {};

            // Get stats by tags
            StreamStatSrv({
                scope: $scope,
                rootId: 'any',
                query: {},
                objectType: 'connector/misp',
                streamObjectType: 'misp',
                field: 'tags',
                sort: ['-count'],
                limit: 5,
                result: {},
                success: function(data){
                    self.byTags = self.prepareResult(data);
                }
            });

            // Get stats by type
            StreamStatSrv({
                scope: $scope,
                rootId: 'any',
                query: {},
                objectType: 'connector/misp',
                streamObjectType: 'misp',
                field: 'eventStatus',
                result: {},
                success: function(data){
                    self.byStatus = self.prepareResult(data);
                }
            });

            // Get stats by ioc
            StreamStatSrv({
                scope: $scope,
                rootId: 'any',
                query: {},
                objectType: 'connector/misp',
                streamObjectType: 'misp',
                field: 'org',
                sort: ['-count'],
                limit: 5,
                result: {},
                success: function(data){
                    self.bySources = self.prepareResult(data);
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
                this.filtering.addFilter(field, value);
            };
        }
    );
})();
