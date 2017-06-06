(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseLinksCtrl',
        function($scope, $state, $stateParams, $uibModal, $timeout, CaseTabsSrv, CaseResolutionStatus) {
            $scope.caseId = $stateParams.caseId;
            $scope.linkStats = [];
            $scope.currentFilter = '';
            $scope.filtering = {}
            var tabName = 'links-' + $scope.caseId;

            // Add tab
            CaseTabsSrv.addTab(tabName, {
                name: tabName,
                label: 'Links',
                closable: true,
                state: 'app.case.links',
                params: {}
            });

            // Select tab
            $timeout(function() {
                CaseTabsSrv.activateTab(tabName);
            }, 0);

            $scope.initStats = function(data) {
                var stats = {
                    'Open': 0
                };

                // Init the stats object
                _.each(_.without(_.keys(CaseResolutionStatus), 'Duplicated'), function(key) {
                    stats[key] = 0
                });

                _.each(data, function(item) {
                    if(item.status === 'Open') {
                        stats[item.status] = stats[item.status] + 1;
                    } else {
                        stats[item.resolutionStatus] = stats[item.resolutionStatus] + 1;
                    }
                });

                var result = [];
                _.each(_.keys(stats), function(key) {
                    result.push({
                        key: key,
                        value: stats[key]
                    })
                });

                return result;
            };

            $scope.filterLinks = function(filter) {
                $scope.currentFilter = filter;
                if(filter === '') {
                    $scope.filtering = {};
                } else if(filter === 'Open') {
                    $scope.filtering = {
                        status: filter
                    };
                } else {
                    $scope.filtering = {
                        resolutionStatus: filter
                    };
                }
            };

            $scope.$watch('links', function(data){
                $scope.linkStats = $scope.initStats(data);
            });
        }
    );
})();
