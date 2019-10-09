(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseAlertsCtrl',
        function($scope, $state, $stateParams, $uibModal, $timeout, CaseTabsSrv, VersionSrv, alerts) {
            $scope.caseId = $stateParams.caseId;
            $scope.alerts = alerts;
            $scope.alertStats = [];
            $scope.currentFilter = '';
            $scope.filtering = {};
            $scope.sorting = {
              field: '-date'
            };
            var tabName = 'alerts-' + $scope.caseId;

            $scope.mispUrls = VersionSrv.mispUrls();

            // Add tab
            CaseTabsSrv.addTab(tabName, {
                name: tabName,
                label: 'Related Alerts',
                closable: true,
                state: 'app.case.alerts',
                params: {}
            });

            // Select tab
            $timeout(function() {
                CaseTabsSrv.activateTab(tabName);
            }, 0);

            $scope.initStats = function(data) {
                var stats = {
                    type: {},
                    source: {}
                };

                // Init the stats object
                _.each(data, function(item) {
                    stats.type[item.type] = stats.type[item.type] ? stats.type[item.type] + 1 : 1;
                    stats.source[item.source] = stats.source[item.source] ? stats.source[item.source] + 1 : 1;
                });

                var result = {};
                Object.keys(stats).forEach(function(field) {
                    result[field] = [];
                    Object.keys(stats[field]).forEach(function(key) {
                        result[field].push({
                            key: key,
                            value: stats[field][key]
                        });
                    });
                });

                return result;
            };

            $scope.filterBy = function(field, filter) {
                $scope.currentFilter = filter;
                if(field === '') {
                    $scope.filtering = {};
                } else {
                    var temp = {};
                    temp[field] = filter;
                    $scope.filtering = temp;
                }
            };

            $scope.sortBy = function(field) {
                if($scope.sorting.field.substr(1) !== field) {
                    $scope.sorting.field = '+' + field;
                } else {
                    $scope.sorting.field = ($scope.sorting.field === '+' + field) ? '-'+field : '+'+field;
                }
            };

            $scope.previewEvent = function(event) {
                $uibModal.open({
                    templateUrl: 'views/partials/alert/event.dialog.html',
                    controller: 'AlertEventCtrl',
                    controllerAs: 'dialog',
                    size: 'max',
                    resolve: {
                        event: event,
                        templates: function() {
                            //return CaseTemplateSrv.list();
                            return [];
                        },
                        isAdmin: false,
                        readonly: true
                    }
                });
            };

            $scope.alertStats = $scope.initStats($scope.alerts);
        }
    );
})();
