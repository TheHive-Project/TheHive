/**
 * Controller for About TheHive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('ObservablesStatsCtrl',
        function($rootScope, $scope, $stateParams, $timeout, StatSrv, StreamStatSrv) {
            var self = this;

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

            self.$onInit = function() {

                // Get stats by tags
                StreamStatSrv({
                    scope: $scope,
                    rootId: $stateParams.caseId,
                    query: defaultQuery,
                    objectType: 'case_artifact',
                    field: 'tags',
                    sort: ['_count'],
                    limit: 10,
                    result: {},
                    success: function(data){
                        self.byTags = StatSrv.prepareResult(data);
                    }
                });

                // Get stats by type
                StreamStatSrv({
                    scope: $scope,
                    rootId: $stateParams.caseId,
                    query: defaultQuery,
                    objectType: 'case_artifact',
                    field: 'dataType',
                    result: {},
                    success: function(data){
                        self.byType = StatSrv.prepareResult(data);
                    }
                });

                // Get stats by ioc
                StreamStatSrv({
                    scope: $scope,
                    rootId: $stateParams.caseId,
                    query: defaultQuery,
                    objectType: 'case_artifact',
                    field: 'ioc',
                    result: {},
                    success: function(data){
                        self.byIoc = StatSrv.prepareResult(data);
                    }
                });
            };            
        }
    );
})();
