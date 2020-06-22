/**
 * Controller for About TheHive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('CaseStatsCtrl',
        function($rootScope, $scope, $stateParams, $timeout, StatSrv, StreamStatSrv) {
            var self = this;

            this.byResolution = {};
            this.byStatus = {};
            this.byTags = {};

            self.$onInit = function() {

                // Get stats by tags
                StreamStatSrv({
                    scope: $scope,
                    rootId: 'any',
                    query: {},
                    objectType: 'case',
                    field: 'tags',
                    sort: ['-count'],
                    limit: 5,
                    result: {},
                    success: function(data){
                        self.byTags = StatSrv.prepareResult(data);
                    }
                });

                // Get stats by type
                StreamStatSrv({
                    scope: $scope,
                    rootId: 'any',
                    query: {},
                    objectType: 'case',
                    field: 'status',
                    result: {},
                    success: function(data){
                        self.byStatus = StatSrv.prepareResult(data);
                    }
                });

                // Get stats by ioc
                StreamStatSrv({
                    scope: $scope,
                    rootId: 'any',
                    query: {},
                    objectType: 'case',
                    field: 'resolutionStatus',
                    result: {},
                    success: function(data){
                        self.byResolution = StatSrv.prepareResult(data);
                    }
                });
            };

            // this.prepareResult = function(rawStats) {
            //     var total = rawStats.count;
            //
            //     var keys = _.without(_.keys(rawStats), 'count');
            //     var columns = keys.map(function(key) {
            //         return {
            //             key: key,
            //             count: rawStats[key].count
            //         };
            //     });
            //
            //     return {
            //         total: total,
            //         details: _.sortBy(columns, 'count').reverse()
            //     };
            // };
        }
    );
})();
