/**
 * Controller for About TheHive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('ObservablesStatsCtrl',
        function($rootScope, $scope, $stateParams, $timeout, StatSrv, StreamQuerySrv) {
            var self = this;

            this.byType = {};
            this.byIoc = {};
            this.byTags = {};

            this.iocLabels = {
                'true': 'IOC',
                'false': 'Not IOC'
            };

            this.iocValues = {
                'true': true,
                'false': false
            };

            self.$onInit = function() {
                var caseId = $stateParams.caseId;

                // Get stats by tags
                StreamQuerySrv('v1', [
                    { _name: 'getCase', idOrName: caseId },
                    { _name: 'observables' },
                    {
                       _name: 'aggregation',
                       _agg: 'field',
                       _field: 'tags',
                       _select: [
                           { _agg: 'count' }
                       ],
                       _order: [ '-count' ],
                       _size: 10
                   }
                ], {
                    scope: $scope,
                    rootId: caseId,
                    objectType: 'case_artifact',
                    query: {
                        params: {
                            name: 'observables-by-tags-stats-' + caseId
                        }
                    },
                    onUpdate: function(data) {
                        self.byTags = StatSrv.prepareResult(data);
                    }
                });


                // Get stats by type
                StreamQuerySrv('v1', [
                    { _name: 'getCase', idOrName: caseId },
                    { _name: 'observables' },
                    {
                       _name: 'aggregation',
                       _agg: 'field',
                       _field: 'dataType',
                       _select: [
                           { _agg: 'count' }
                       ]
                   }
                ], {
                    scope: $scope,
                    rootId: caseId,
                    objectType: 'case_artifact',
                    query: {
                        params: {
                            name: 'observables-by-type-stats-' + caseId
                        }
                    },
                    onUpdate: function(data) {
                        self.byType = StatSrv.prepareResult(data);
                    }
                });

                // Get stats by ioc
                StreamQuerySrv('v1', [
                    { _name: 'getCase', idOrName: caseId },
                    { _name: 'observables' },
                    {
                       _name: 'aggregation',
                       _agg: 'field',
                       _field: 'ioc',
                       _select: [
                           { _agg: 'count' }
                       ]
                   }
                ], {
                    scope: $scope,
                    rootId: caseId,
                    objectType: 'case_artifact',
                    query: {
                        params: {
                            name: 'observables-by-ioc-stats-' + caseId
                        }
                    },
                    onUpdate: function(data) {
                        self.byIoc = StatSrv.prepareResult(data);
                    }
                });
            };
        }
    );
})();
