/**
 * Controller for About TheHive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AlertStatsCtrl',
        function($rootScope, $scope, $stateParams, $timeout, StatSrv, StreamStatSrv, FilteringSrv) {
            var self = this;

            this.filtering = FilteringSrv;

            this.byType = {};
            this.byStatus = {};
            this.byTags = {};

            self.$onInit = function() {

                // Get stats by tags
                StreamStatSrv({
                    scope: $scope,
                    rootId: 'any',
                    query: {},
                    objectType: 'alert',
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
                    objectType: 'alert',
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
                    objectType: 'alert',
                    field: 'type',
                    sort: ['-count'],
                    limit: 5,
                    result: {},
                    success: function(data){
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
