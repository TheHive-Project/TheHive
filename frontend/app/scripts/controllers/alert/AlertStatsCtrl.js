/**
 * Controller for About TheHive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AlertStatsCtrl',
        function($rootScope, $scope, $stateParams, $timeout, StatSrv, StreamQuerySrv, FilteringSrv) {
            var self = this;

            this.filtering = FilteringSrv;

            this.byType = {};
            this.byStatus = {};
            this.byTags = {};

            self.$onInit = function() {

                // Get stats by tags
                StreamQuerySrv('v1', [
                    { _name: 'listAlert' },
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
                    objectType: 'alert',
                    query: {
                        params: {
                            name: 'alert-by-tags-stats'
                        }
                    },
                    onUpdate: function(data) {
                        self.byTags = StatSrv.prepareResult(data);
                    }
                });

                // Get stats by read status
                StreamQuerySrv('v1', [
                    { _name: 'listAlert' },
                    {
                       _name: 'aggregation',
                       _agg: 'field',
                       _field: 'read',
                       _select: [
                           { _agg: 'count' }
                       ]
                   }
                ], {
                    scope: $scope,
                    rootId: 'any',
                    objectType: 'alert',
                    query: {
                        params: {
                            name: 'alert-by-read-status-stats'
                        }
                    },
                    onUpdate: function(data) {
                        self.byStatus = StatSrv.prepareResult(data);
                    }
                });

                // Get stats by ioc
                StreamQuerySrv('v1', [
                    { _name: 'listAlert' },
                    {
                       _name: 'aggregation',
                       _agg: 'field',
                       _field: 'type',
                       _select: [
                           { _agg: 'count' }
                       ],
                       _order: [ '-count' ],
                       _size: 5
                   }
                ], {
                    scope: $scope,
                    rootId: 'any',
                    objectType: 'alert',
                    query: {
                        params: {
                            name: 'alert-by-type-stats'
                        }
                    },
                    onUpdate: function(data) {
                        self.byType = StatSrv.prepareResult(data);
                    }
                });
            };

            this.filterBy = function(field, value) {
                this.filtering.addFilter(field, value);
            };
        }
    );
})();
