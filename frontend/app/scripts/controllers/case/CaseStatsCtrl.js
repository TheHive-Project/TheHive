/**
 * Controller for About TheHive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('CaseStatsCtrl',
        function($rootScope, $scope, $stateParams, $timeout, StatSrv, StreamQuerySrv) {
            var self = this;

            this.byResolution = {};
            this.byStatus = {};
            this.byTags = {};

            self.$onInit = function() {
                // Get stats by tags
                StreamQuerySrv('v1', [
                    { _name: 'listCase' },
                    {
                       _name: 'aggregation',
                       _agg: 'field',
                       _field: 'tags',
                       _select: [
                           { _agg: 'count' }
                       ],
                       _order: [ '-count' ],
                       _size: 5
                   }
                ], {
                    scope: $scope,
                    rootId: 'any',
                    objectType: 'case',
                    query: {
                        params: {
                            name: 'case-by-tags-stats'
                        }
                    },
                    onUpdate: function(data) {
                        self.byTags = StatSrv.prepareResult(data);
                    }
                });

                // Get stats by status
                StreamQuerySrv('v1', [
                    { _name: 'listCase' },
                    {
                       _name: 'aggregation',
                       _agg: 'field',
                       _field: 'status',
                       _select: [
                           { _agg: 'count' }
                       ]
                   }
                ], {
                    scope: $scope,
                    rootId: 'any',
                    objectType: 'case',
                    query: {
                        params: {
                            name: 'case-by-status-stats'
                        }
                    },
                    onUpdate: function(data) {
                        self.byStatus = StatSrv.prepareResult(data);
                    }
                });

                // Get stats by resolution status
                StreamQuerySrv('v1', [
                    { _name: 'listCase' },
                    {
                       _name: 'aggregation',
                       _agg: 'field',
                       _field: 'resolutionStatus',
                       _select: [
                           { _agg: 'count' }
                       ]
                   }
                ], {
                    scope: $scope,
                    rootId: 'any',
                    objectType: 'case',
                    query: {
                        params: {
                            name: 'case-by-resolution-status-stats'
                        }
                    },
                    onUpdate: function(data) {
                        self.byResolution = StatSrv.prepareResult(data);
                    }
                });
            };
        }
    );
})();
